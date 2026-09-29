const fs = require('fs');
const CHANNEL = 't2x2_video';
const URL = `https://t.me/s/${CHANNEL}`;
const CATALOG = 'data/videos.json';

function decode(s='') {
  return s.replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>');
}
function strip(s='') {
  return decode(s.replace(/<br\s*\/?>/gi,'\n').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim());
}
function readPrevious() {
  try {
    return JSON.parse(fs.readFileSync(CATALOG, 'utf8'));
  } catch (_) {
    return { channel: CHANNEL, updatedAt: null, count: 0, videos: [] };
  }
}
function stableVideos(value) {
  return JSON.stringify((value || []).map(v => ({
    id: v.id,
    postUrl: v.postUrl,
    videoUrl: v.videoUrl,
    thumbnail: v.thumbnail,
    title: v.title,
    datetime: v.datetime,
    duration: v.duration
  })));
}

(async()=>{
  const previous = readPrevious();
  const res = await fetch(URL,{
    redirect:'follow',
    headers:{
      'user-agent':'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Safari/537.36',
      'accept':'text/html,application/xhtml+xml',
      'accept-language':'ru-RU,ru;q=0.9,en;q=0.8',
      'cache-control':'no-cache'
    }
  });
  if(!res.ok) throw new Error(`Telegram HTTP ${res.status}`);

  const html = await res.text();
  const blocks = html.split('tgme_widget_message_wrap').slice(1);
  if(!blocks.length) {
    throw new Error(
      `Telegram public preview returned no message blocks (HTML ${html.length} bytes). ` +
      'Keeping the previous catalog instead of overwriting it with an empty one.'
    );
  }

  const videos = [];
  for(const block of blocks){
    const post = block.match(/data-post="([^"]+)"/)?.[1];
    if(!post) continue;
    const id = post.split('/').pop();
    const videoUrl =
      block.match(/<video[^>]+src="([^"]+)"/i)?.[1] ||
      block.match(/<source[^>]+src="([^"]+)"/i)?.[1] ||
      null;
    const thumb =
      block.match(/background-image:url\(['"]?([^'")]+)['"]?\)/i)?.[1] ||
      block.match(/<img[^>]+src="([^"]+)"/i)?.[1] ||
      null;
    const textHtml = block.match(/tgme_widget_message_text[^>]*>([\s\S]*?)<\/div>/i)?.[1] || '';
    const title = strip(textHtml).slice(0,180) || `Запись стрима #${id}`;
    const datetime = block.match(/datetime="([^"]+)"/i)?.[1] || null;
    const duration = strip(block.match(/tgme_widget_message_video_duration[^>]*>([\s\S]*?)<\/div>/i)?.[1] || '');
    if(videoUrl){
      videos.push({
        id,
        postUrl:`https://t.me/${CHANNEL}/${id}`,
        videoUrl:decode(videoUrl),
        thumbnail:thumb ? decode(thumb) : null,
        title,
        datetime,
        duration
      });
    }
  }

  console.log(`Telegram HTML bytes: ${html.length}; message blocks: ${blocks.length}; playable videos: ${videos.length}`);

  if(!videos.length) {
    throw new Error(
      'Telegram returned message blocks but no direct playable videos. ' +
      'Keeping the previous catalog instead of publishing an empty result.'
    );
  }

  if(stableVideos(previous.videos) === stableVideos(videos)) {
    console.log('Catalog is already current; no file rewrite needed.');
    return;
  }

  fs.mkdirSync('data',{recursive:true});
  fs.writeFileSync(CATALOG,JSON.stringify({
    channel:CHANNEL,
    updatedAt:new Date().toISOString(),
    count:videos.length,
    videos
  },null,2));
  console.log(`Catalog updated: ${previous.count || 0} -> ${videos.length} videos`);
})().catch(error => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
