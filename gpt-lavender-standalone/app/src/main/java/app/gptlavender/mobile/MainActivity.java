package app.gptlavender.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.text.util.Linkify;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final String MODEL = "gpt-5.6-sol";
    private static final String INSTRUCTIONS = "Ты — основной ИИ пользователя в одном постоянном чате. Отвечай по-русски, если пользователь не перешел на другой язык. Пиши аккуратно и ясно. Перед ответом проверяй логику и факты. Если вопрос зависит от свежей публичной информации, используй web_search. Не придумывай источники, даты, версии, цены или результаты поиска. Если есть неопределенность — прямо скажи об этом.";
    private LinearLayout chat;
    private EditText input;
    private Button send;
    private TextView status;
    private SharedPreferences prefs;
    private final ArrayList<JSONObject> history = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("gpt_lavender", MODE_PRIVATE);
        buildUi();
        loadHistory();
        if (prefs.getString("api_key", "").isEmpty()) showKeyDialog(false);
    }

    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi(){
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("ГПТ 🪻"); title.setTextSize(23); title.setTypeface(null, 1);
        status = new TextView(this); status.setText(MODEL + " · один чат"); status.setTextSize(12); status.setAlpha(.65f);
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(title); titleBox.addView(status);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = new Button(this); settings.setText("Настройки"); settings.setOnClickListener(v -> showKeyDialog(true));
        header.addView(settings);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        chat = new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chat.setPadding(0, dp(10), 0, dp(12));
        scroll.addView(chat, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.BOTTOM);
        input = new EditText(this); input.setHint("Сообщение"); input.setMaxLines(5); input.setSingleLine(false); input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, e) -> { if(actionId == EditorInfo.IME_ACTION_SEND){ send(); return true; } return false; });
        send = new Button(this); send.setText("↑"); send.setTextSize(22); send.setOnClickListener(v -> send());
        bottom.addView(input, new LinearLayout.LayoutParams(0, -2, 1)); bottom.addView(send, new LinearLayout.LayoutParams(dp(58), dp(58)));
        root.addView(bottom);
        setContentView(root);
    }

    private void addBubble(String role, String text, JSONArray sources, boolean persist){
        TextView tv = new TextView(this);
        tv.setTextSize(15); tv.setLineSpacing(0, 1.16f); tv.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(role.equals("user") ? dp(330) : -1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        if(role.equals("user")){ lp.gravity = Gravity.END; tv.setBackgroundColor(Color.rgb(224,214,255)); }
        else { tv.setBackgroundColor(Color.TRANSPARENT); }
        StringBuilder out = new StringBuilder(text == null ? "" : text);
        if(sources != null && sources.length() > 0){
            out.append("\n\nИсточники:");
            for(int i=0;i<Math.min(8, sources.length());i++){
                JSONObject s = sources.optJSONObject(i); if(s==null) continue;
                String title=s.optString("title"); String url=s.optString("url");
                if(!url.isEmpty()) out.append("\n").append(title.isEmpty()?url:title).append(" — ").append(url);
            }
        }
        tv.setText(out.toString()); Linkify.addLinks(tv, Linkify.WEB_URLS); tv.setLinksClickable(true);
        chat.addView(tv, lp);
        if(persist){
            try { JSONObject o=new JSONObject().put("role",role).put("text",text); if(sources!=null)o.put("sources",sources); history.add(o); saveHistory(); } catch(Exception ignored){}
        }
    }

    private void send(){
        String text=input.getText().toString().trim(); if(text.isEmpty()) return;
        String key=prefs.getString("api_key",""); if(key.isEmpty()){ showKeyDialog(false); return; }
        input.setText(""); addBubble("user", text, null, true); setBusy(true, "Думаю и проверяю…");
        new Thread(() -> request(key, text)).start();
    }

    private void request(String key, String userText){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(20000); c.setReadTimeout(240000); c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            JSONObject p=new JSONObject().put("model",MODEL).put("instructions",INSTRUCTIONS).put("input",userText)
                .put("tools", new JSONArray().put(new JSONObject().put("type","web_search")));
            String prev=prefs.getString("previous_response_id",""); if(!prev.isEmpty()) p.put("previous_response_id",prev);
            c.getOutputStream().write(p.toString().getBytes(StandardCharsets.UTF_8));
            int code=c.getResponseCode(); InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            String body=readAll(is);
            if(code<200||code>=300){ String m="OpenAI API: ошибка "+code; try{m=new JSONObject(body).getJSONObject("error").optString("message",m);}catch(Exception ignored){} throw new Exception(m); }
            JSONObject r=new JSONObject(body); String rid=r.optString("id"); if(!rid.isEmpty()) prefs.edit().putString("previous_response_id",rid).apply();
            JSONArray output=r.optJSONArray("output"); StringBuilder answer=new StringBuilder(); JSONArray sources=new JSONArray(); HashSet<String> seen=new HashSet<>();
            if(output!=null) for(int i=0;i<output.length();i++){
                JSONObject item=output.optJSONObject(i); if(item==null||!"message".equals(item.optString("type"))) continue;
                JSONArray content=item.optJSONArray("content"); if(content==null) continue;
                for(int j=0;j<content.length();j++){
                    JSONObject part=content.optJSONObject(j); if(part==null||!"output_text".equals(part.optString("type"))) continue;
                    String t=part.optString("text"); if(!t.isEmpty()){ if(answer.length()>0) answer.append("\n"); answer.append(t); }
                    JSONArray anns=part.optJSONArray("annotations"); if(anns!=null) for(int k=0;k<anns.length();k++){
                        JSONObject a=anns.optJSONObject(k); if(a==null)continue; String url=a.optString("url"); String title=a.optString("title");
                        JSONObject u=a.optJSONObject("url_citation"); if(url.isEmpty()&&u!=null){url=u.optString("url"); title=u.optString("title");}
                        if(!url.isEmpty()&&seen.add(url)) sources.put(new JSONObject().put("title",title).put("url",url));
                    }
                }
            }
            String finalAnswer=answer.length()==0?"Не удалось получить текст ответа.":answer.toString();
            runOnUiThread(() -> { addBubble("assistant",finalAnswer,sources,true); setBusy(false,MODEL+" · один чат"); });
        } catch(Exception e){ String m=e.getMessage()==null?"Не удалось получить ответ.":e.getMessage(); runOnUiThread(() -> { Toast.makeText(this,m,Toast.LENGTH_LONG).show(); setBusy(false,MODEL+" · один чат"); }); }
        finally{ if(c!=null)c.disconnect(); }
    }

    private static String readAll(InputStream is) throws Exception { if(is==null)return ""; BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); String l; while((l=br.readLine())!=null)b.append(l).append('\n'); return b.toString(); }
    private void setBusy(boolean b,String s){ send.setEnabled(!b); input.setEnabled(!b); status.setText(s); }

    private void showKeyDialog(boolean cancellable){
        EditText key=new EditText(this); key.setHint("sk-..."); key.setSingleLine(true); key.setText(prefs.getString("api_key",""));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("OpenAI API key").setMessage("Ключ не зашит в APK. Он сохраняется только на этом устройстве.")
            .setView(key).setPositiveButton("Сохранить",(x,w)->{String v=key.getText().toString().trim(); if(!v.isEmpty())prefs.edit().putString("api_key",v).apply();})
            .setNeutralButton("Очистить чат",(x,w)->{history.clear(); chat.removeAllViews(); prefs.edit().remove("messages").remove("previous_response_id").apply();}).create();
        d.setCanceledOnTouchOutside(cancellable); d.setCancelable(cancellable); d.show();
    }

    private void saveHistory(){ JSONArray a=new JSONArray(); for(JSONObject o:history)a.put(o); prefs.edit().putString("messages",a.toString()).apply(); }
    private void loadHistory(){
        try{ JSONArray a=new JSONArray(prefs.getString("messages","[]")); for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null)continue; history.add(o); addBubble(o.optString("role"),o.optString("text"),o.optJSONArray("sources"),false); } }catch(Exception ignored){}
    }
}
