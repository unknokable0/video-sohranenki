import express from "express";
import OpenAI from "openai";

const app = express();
app.use(express.json({ limit: "1mb" }));

const port = Number(process.env.PORT || 8787);
const model = process.env.OPENAI_MODEL || "gpt-5.6-sol";
const reasoningEffort = process.env.OPENAI_REASONING_EFFORT || "high";

const SYSTEM_INSTRUCTIONS = `
Ты — основной AI-помощник в личном приложении SOHR AI.

Правила ответа:
- По умолчанию отвечай на языке пользователя.
- Сначала разберись в задаче, затем отвечай. Не угадывай важные факты.
- Для свежей, меняющейся или сомнительной информации используй web search и перепроверяй факты.
- Если использовал интернет, опирайся на надёжные источники и ясно отделяй проверенные факты от предположений.
- Отвечай прямо. Не растягивай ответ без причины и не повторяй вопрос пользователя.
- Когда пользователь просит рекомендацию, старайся дать один основной практичный вариант, а альтернативы добавляй только если они реально нужны.
- Используй аккуратный Markdown: короткие абзацы, уместные заголовки, списки только когда они улучшают читаемость.
- Не выдумывай, что что-то проверил, запустил, открыл или сделал, если этого не было.
- Учитывай весь переданный контекст этого единственного чата.
- Если задача сложная, приоритет — точность и продуманность, а не скорость ответа.
`.trim();

function writeEvent(res, payload) {
  res.write(JSON.stringify(payload) + "\n");
}

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "sohr-ai",
    model
  });
});

app.post("/v1/chat", async (req, res) => {
  if (!process.env.OPENAI_API_KEY) {
    return res.status(503).json({
      error: "OPENAI_API_KEY is not configured on the server"
    });
  }

  const rawMessages = Array.isArray(req.body?.messages)
    ? req.body.messages
    : [];

  const messages = rawMessages
    .filter((item) =>
      item &&
      (item.role === "user" || item.role === "assistant") &&
      typeof item.content === "string" &&
      item.content.trim()
    )
    .slice(-60)
    .map((item) => ({
      role: item.role,
      content: item.content
    }));

  if (!messages.length || messages[messages.length - 1].role !== "user") {
    return res.status(400).json({ error: "A final user message is required" });
  }

  res.status(200);
  res.setHeader("Content-Type", "application/x-ndjson; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache, no-transform");
  res.setHeader("X-Accel-Buffering", "no");
  res.flushHeaders?.();

  const client = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
  });

  writeEvent(res, { type: "status", text: "Думаю…" });

  try {
    const stream = await client.responses.create({
      model,
      reasoning: {
        effort: reasoningEffort
      },
      instructions: SYSTEM_INSTRUCTIONS,
      input: messages,
      tools: [
        { type: "web_search" }
      ],
      stream: true
    });

    let responseId = null;
    let announcedSearch = false;

    for await (const event of stream) {
      if (
        event.type === "response.web_search_call.searching" ||
        event.type === "response.web_search_call.in_progress"
      ) {
        if (!announcedSearch) {
          announcedSearch = true;
          writeEvent(res, {
            type: "status",
            text: "Проверяю источники…"
          });
        }
        continue;
      }

      if (event.type === "response.output_text.delta") {
        writeEvent(res, {
          type: "delta",
          text: event.delta
        });
        continue;
      }

      if (event.type === "response.completed") {
        responseId = event.response?.id || null;
      }

      if (event.type === "response.failed") {
        const message =
          event.response?.error?.message ||
          "OpenAI response failed";
        throw new Error(message);
      }
    }

    writeEvent(res, {
      type: "status",
      text: "Готов"
    });
    writeEvent(res, {
      type: "done",
      responseId
    });
    res.end();
  } catch (error) {
    writeEvent(res, {
      type: "error",
      message: error instanceof Error
        ? error.message
        : "Unknown server error"
    });
    res.end();
  }
});

app.listen(port, "0.0.0.0", () => {
  console.log("SOHR AI server listening on :" + port);
});
