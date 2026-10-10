"use strict";
/* KADR v0.2: dynamic series catalog, genuine episode listings, regional viewing links,
   free licensed video playback, personal file playback, and quality-of-life polish.
   Public TVmaze metadata: CC BY-SA, attributed in the app. No copyrighted streams. */
const KADR_TV_API="https://api.tvmaze.com";
const KADR_MOON="File:Le Voyage dans la Lune (1902).webm";
const KADR_COMMONS_PAGE="https://commons.wikimedia.org/wiki/File:Le_Voyage_dans_la_Lune_(1902).webm";
const KADR_JUSTWATCH="https://www.justwatch.com/pl";
const KADR_MENTALIST="https://www.justwatch.com/pl/serial/mentalista";
const kadrEpisodes=new Map();
const kadrPending=new Map();
const kadrSearchCache=new Map();
let kadrSearchTimer=null,kadrSearchSeq=0,kadrVideoBlob=null,kadrVideoLabel="",kadrCurrentVideoUrl="",kadrFetchVideo=false;
const kadrMoon={id:"moon",title:"Путешествие на Луну",en:"A Trip to the Moon",year:1902,type:"Фильм",genre:["Фантастика","Приключения"],tag:"Смотреть бесплатно",color:"linear-gradient(135deg,#8286a8,#52436c 54%,#171525)",desc:"Классика Жоржа Мельеса. Немое кино 1902 года из общественного достояния. Настоящее воспроизведение через Wikimedia Commons; звуковой русской дорожки у немого фильма нет.",legalVideo:true};
if(!films.some(x=>x.id==="moon"))films.push(kadrMoon);
const kadrOriginalRender=render;
const kadrOriginalDetails=details;
const kadrOriginalPlayer=player;
const kadrOriginalHome=home;
const kadrOriginalProfile=profile;
function kadrEpisodesMarkup(x){
  if(!x.seasons)return "";
  const episodes=kadrEpisodes.get(x.id);
  const list=episodes?episodes.filter(e=>e.season===season&&Number.isInteger(e.number)):null;
  const maxSeason=episodes?Math.max(x.seasons,...episodes.map(e=>e.season||1)):x.seasons;
  const options=Array.from({length:maxSeason},(_,i)=>'<option value="'+(i+1)+'"'+(season===i+1?' selected':'')+'>Сезон '+(i+1)+'</option>').join("");
  const header='<div class="episodes-head"><h3>Серии '+(list?'· '+list.length:'')+'</h3><select id="season-select" aria-label="Сезон">'+options+'</select></div>';
  if(!episodes)return header+'<div class="kadr-loading"><span class="kadr-spinner"></span><span>Загружаем настоящий список серий…</span></div>';
  if(!list.length)return header+'<div class="kadr-soft-empty">Для этого сезона пока нет сведений о сериях.</div>';
  return header+list.map(ep=>{
    const thumb=ep.image?.medium;
    const name=ep.name?esc(ep.name):"Серия "+ep.number;
    const date=ep.airdate?new Date(ep.airdate+"T12:00:00").toLocaleDateString("ru-RU",{day:"numeric",month:"long",year:"numeric"}):"Дата не указана";
    return '<button class="episode kadr-episode" data-watch="'+x.id+'" data-episode="'+ep.number+'" aria-label="Открыть серию '+ep.number+'">'+
      '<span class="episode-art">'+(typeof thumb==="string"&&thumb.startsWith("https://static.tvmaze.com/")?'<img alt="" loading="lazy" src="'+esc(thumb)+'">':I("play"))+'</span>'+
      '<span class="kadr-ep-info"><b>'+ep.number+'. '+name+'</b><small>'+date+(ep.runtime?' · '+ep.runtime+' мин':'')+'</small></span>'+
      '<span class="last">'+I("right")+'</span></button>';
  }).join("");
}
details=function(){
  if(!selected)return "";
  const x=find(selected);
  if(!x)return "";
  let markup=kadrOriginalDetails();
  if(x.seasons)markup=markup.replace(/<div class="episodes-head">[\s\S]*?(?=<div class="fineprint">)/,kadrEpisodesMarkup(x));
  const button=x.legalVideo
    ? '<button class="kadr-action kadr-action-feature" data-watch="moon" data-episode="1">'+I("play")+'Смотреть бесплатно</button>'
    : '<button class="kadr-action" data-where="'+x.id+'">'+I("search")+'Где смотреть в Польше</button>';
  const extra='<div class="kadr-extra-actions">'+button+
    '<button class="kadr-action" data-local>'+I("play")+'Мой видеофайл</button></div>'+
    '<div class="kadr-truth"><span class="kadr-truth-dot"></span>'+
    (x.legalVideo?'Доступно законное воспроизведение немого фильма из Wikimedia Commons.':'Сведения о сериях не означают наличие видео. Русская озвучка на площадках пока не подтверждена.')+
    '</div>';
  markup=markup.replace('<div class="fineprint">',extra+'<div class="fineprint">');
  return markup;
};
home=function(){
  const original=kadrOriginalHome();
  const section='<section class="section kadr-free"><div class="section-heading"><div><h2>Можно смотреть сейчас</h2><p>Настоящее видео из общественного достояния</p></div><span class="kadr-accent-label">ЛЕГАЛЬНО</span></div><div class="rail">'+card(kadrMoon)+'</div></section>';
  return original+section;
};
profile=function(){
  return kadrOriginalProfile()+'<section class="section"><div class="section-heading"><div><h2>Свои видео</h2><p>MP4, WebM и другие форматы, поддерживаемые телефоном</p></div></div><button class="primary" data-local>'+I("play")+'Открыть видеофайл</button><p class="kadr-small">Видеофайл останется на твоём устройстве. KADR не отправляет его на сервер.</p></section>';
};
player=function(){
  if(!playing)return "";
  if(playing!=="moon" && playing!=="local")return kadrOriginalPlayer();
  const local=playing==="local",title=local?kadrVideoLabel||"Мой видеофайл":"Путешествие на Луну";
  const videoUrl=local?kadrVideoBlob:kadrCurrentVideoUrl;
  return '<div class="overlay" id="player" role="dialog" aria-modal="true" aria-label="Видеоплеер"><div class="player kadr-real-player">'+
    '<button class="player-back" data-back>'+I("back")+'Назад</button>'+
    '<div class="kadr-player-frame">'+
    (videoUrl?'<video id="kadr-video" controls playsinline preload="metadata" src="'+esc(videoUrl)+'" aria-label="'+esc(title)+'"></video>':
    '<div class="kadr-player-loading"><span class="kadr-spinner"></span><strong>Подбираем видео для просмотра…</strong><span>Нужен интернет для загрузки</span></div>')+
    '</div><div class="player-meta"><span>'+esc(title)+'</span><span>'+(local?'Локальный видеофайл':'Общественное достояние')+'</span></div>'+
    (!local?'<div class="kadr-provider-note">Источник: Wikimedia Commons. Это немой фильм 1902 года. <button data-external="commons">Страница файла '+I("right")+'</button></div>':'')+
    '<p class="kadr-video-error" id="kadr-video-error" hidden>Видео не загрузилось. Попробуй открыть оригинальный файл через Wikimedia Commons.</p>'+
    '</div></div>';
};
render=function(){
  // Preserve overlay reading position during lazy poster fetches and other non-navigation renders.
  const previous=document.querySelector("#details");
  const scroll=previous?.scrollTop||0;
  kadrOriginalRender();
  if(scroll&&document.querySelector("#details"))document.querySelector("#details").scrollTop=scroll;
  kadrDecorate();
  if(selected&&find(selected)?.seasons)kadrLoadEpisodes(find(selected));
  if(playing==="moon"&&!kadrCurrentVideoUrl&&!kadrFetchVideo)kadrLoadPublicVideo();
};
function kadrDecorate(){
  document.documentElement.classList.add("kadr-v2");
  const footerNode=document.querySelector(".footer");
  if(footerNode){
    const a=document.createElement("a");
    a.textContent="Сведения о сериалах: TVmaze · CC BY-SA";a.href="https://www.tvmaze.com/api";a.target="_self";
    a.className="kadr-credit";
    footerNode.appendChild(a);
  }
  const root=document.querySelector(".page");
  if(root&&!document.querySelector("#local-file")){const file=document.createElement("input");file.id="local-file";file.type="file";file.accept="video/*";file.hidden=true;root.appendChild(file)}
  if(tab==="search"){
    const results=document.querySelector("#results");
    if(results&&!document.querySelector("#kadr-remote")){const r=document.createElement("div");r.id="kadr-remote";r.className="kadr-remote";results.after(r)}
    if(query.trim().length>=3)kadrShowCached(query.trim());
  }
  if(playing==="moon"&&kadrCurrentVideoUrl)kadrAttachVideoEvents();
}
function kadrShowCached(q){
  const dest=document.querySelector("#kadr-remote");if(!dest)return;
  const list=kadrSearchCache.get(q.toLowerCase());
  if(list===undefined){dest.innerHTML='<div class="kadr-search-hint">Ищем другие сериалы в базе TVmaze…</div>';return}
  if(list===null){dest.innerHTML='<div class="kadr-search-hint">Поиск в сети временно недоступен. Проверь интернет.</div>';return}
  const visible=list.filter(x=>!films.some(f=>f.id===x.id&&f.id[0]!=="t")&&!films.some(f=>f.id!==x.id&&f.en.toLowerCase()===x.en.toLowerCase()));
  dest.innerHTML=visible.length?'<section class="section"><div class="section-heading"><div><h2>Ещё сериалы</h2><p>Онлайн-каталог TVmaze · данные о сериалах, не видео</p></div></div><div class="grid">'+visible.slice(0,18).map(card).join("")+'</div></section>':"";
}
let kadrEpisodeVersion=0;
async function kadrLoadEpisodes(x){
  if(kadrEpisodes.has(x.id)||kadrPending.has(x.id))return;
  let id=x.tvShowId;
  const promise=(async()=>{
    try{
      if(!id){
        const r=await fetch(KADR_TV_API+"/singlesearch/shows?q="+encodeURIComponent(x.tv||x.en));
        if(!r.ok)throw Error("show missing");
        const show=await r.json();id=show.id;
      }
      const r=await fetch(KADR_TV_API+"/shows/"+id+"/episodes");
      if(!r.ok)throw Error("episodes unavailable");
      const all=await r.json();
      if(!Array.isArray(all)||!all.length)throw Error("no episode data");
      kadrEpisodes.set(x.id,all);
      if(selected===x.id&&!playing)render();
    }catch(e){
      // Avoid a forever-loading spinner and explain when source is unavailable.
      kadrEpisodes.set(x.id,[]);
      if(selected===x.id&&!playing)render();
    }finally{kadrPending.delete(x.id)}
  })();
  kadrPending.set(x.id,promise);
}
async function kadrRemoteSearch(q){
  const key=q.toLowerCase(),seq=++kadrSearchSeq;
  if(kadrSearchCache.has(key)){kadrShowCached(q);return}
  const localMatch=films.find(x=>x.title.toLocaleLowerCase("ru").includes(key)&&x.tv);
  const term=localMatch?.tv||q;
  try{
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),7000);
    const r=await fetch(KADR_TV_API+"/search/shows?q="+encodeURIComponent(term),{signal:controller.signal});
    clearTimeout(timeout);
    if(!r.ok)throw Error("network");
    const data=await r.json();
    const matches=(Array.isArray(data)?data:[]).slice(0,18).map(row=>{
      const s=row.show||{},id="tv"+s.id,orig=String(s.name||"");
      if(!orig||!s.id)return null;
      const year=Number(String(s.premiered||"").slice(0,4))||2020;
      const genres=Array.isArray(s.genres)&&s.genres.length?s.genres.slice(0,2):["Драма"];
      const one={
        id,title:orig,en:orig,year,type:"Сериал",seasons:1,genre:genres,
        tag:"TVmaze",color:"linear-gradient(150deg,#695579,#3b345a 62%,#1b182c)",
        desc:"Описание и список настоящих серий доступны в онлайн-каталоге TVmaze.",tvShowId:s.id,tv:s.name
      };
      const poster=s.image?.medium;
      if(typeof poster==="string"&&poster.startsWith("https://static.tvmaze.com/"))images[id]=poster;
      if(!films.some(f=>f.id===id))films.push(one);
      return one;
    }).filter(Boolean);
    kadrSearchCache.set(key,matches);
  }catch(e){kadrSearchCache.set(key,null)}
  if(seq===kadrSearchSeq&&tab==="search"&&query.trim().toLowerCase()===key)kadrShowCached(q);
}
async function kadrLoadPublicVideo(){
  kadrFetchVideo=true;
  try{
    const url="https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*&prop=videoinfo&viprop=derivatives&titles="+encodeURIComponent(KADR_MOON);
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),11000);
    const res=await fetch(url,{signal:controller.signal});
    clearTimeout(timer);
    if(!res.ok)throw Error("commons offline");
    const json=await res.json();
    const pages=Object.values(json.query?.pages||{});
    const variants=pages[0]?.videoinfo?.[0]?.derivatives||[];
    const suitable=variants.filter(v=>typeof v.src==="string"&&v.src.startsWith("https://upload.wikimedia.org/")&&(v.type||"").includes("video/"));
    const preferred=suitable.find(v=>(v.type||"").includes("mp4")&&(v.height||0)<=480)||
                    suitable.find(v=>(v.type||"").includes("webm")&&(v.height||0)>=360&&(v.height||0)<=480)||
                    suitable.find(v=>(v.type||"").includes("webm")&&(v.height||0)<=720);
    if(!preferred)throw Error("no compatible transcode");
    kadrCurrentVideoUrl=preferred.src;
    if(playing==="moon")render();
  }catch(e){
    if(playing==="moon"){const el=document.querySelector(".kadr-player-loading");if(el)el.innerHTML='<strong>Не удалось загрузить видео</strong><span>Попробуй открыть оригинал в Wikimedia Commons.</span>'}
  }finally{kadrFetchVideo=false}
}
function kadrAttachVideoEvents(){
  const v=document.querySelector("#kadr-video");if(!v||v.dataset.wired)return;v.dataset.wired="yes";
  v.addEventListener("error",()=>{
    const el=document.getElementById("kadr-video-error");if(el)el.hidden=false;
  });
}
document.addEventListener("input",event=>{
 if(event.target.id==="search"){
   clearTimeout(kadrSearchTimer);
   const q=event.target.value.trim();
   const dest=document.getElementById("kadr-remote");
   if(q.length<3){if(dest)dest.innerHTML="";return}
   kadrShowCached(q);
   kadrSearchTimer=setTimeout(()=>kadrRemoteSearch(q),400);
 }
 if(event.target.id==="local-file"){
   const file=event.target.files?.[0];
   if(!file)return;
   if(kadrVideoBlob)URL.revokeObjectURL(kadrVideoBlob);
   kadrVideoBlob=URL.createObjectURL(file);
   kadrVideoLabel=file.name||"Мой видеофайл";
   playing="local";render();
 }
});
document.addEventListener("click",event=>{
 const el=event.target.closest("button");if(!el)return;
 if(el.hasAttribute("data-where")){
   event.stopImmediatePropagation();
   const x=find(el.getAttribute("data-where"));
   const url=x?.id==="mentalist"?KADR_MENTALIST:KADR_JUSTWATCH;
   window.location.href=url;
 }
 if(el.hasAttribute("data-local")){
   event.stopImmediatePropagation();
   const i=document.querySelector("#local-file");if(i){i.value="";i.click()}
 }
 if(el.hasAttribute("data-external")){
   event.stopImmediatePropagation();
   if(el.dataset.external==="commons")window.location.href=KADR_COMMONS_PAGE;
 }
},true);
document.addEventListener("visibilitychange",()=>{if(document.hidden)document.querySelector("video")?.pause()});
render();