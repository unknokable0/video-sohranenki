const fs = require('fs');
const CHANNEL = 't2x2_video';
const URL = `https://t.me/s/${CHANNEL}`;

function decode(s='') {
  return s.replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>');
}
function strip(s='') {
  return decode(s.replace(/<br\s*\/?>/gi,'\n').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim());
}
(async()=>{
  const res = await fetch(URL,{headers:{'user-agent':'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Safari/537.36'}});
  if(!res.ok) throw new Error(`Telegram HTTP ${res.status}`);
  const html = await res.text();
  const blocks = html.split('tgme_widget_message_wrap').slice(1);
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
  fs.mkdirSync('data',{recursive:true});
  fs.writeFileSync('data/videos.json',JSON.stringify({
    channel:CHANNEL,
    updatedAt:new Date().toISOString(),
    count:videos.length,
    videos
  },null,2));
  console.log(`Telegram HTML bytes: ${html.length}; message blocks: ${blocks.length}; playable videos: ${videos.length}`);
  if(!videos.length){
    fs.writeFileSync('data/telegram-debug.html',html);
    console.log('No direct playable videos found; wrote debug HTML for inspection.');
  }
})();