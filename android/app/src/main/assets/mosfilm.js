"use strict";
/* KADR Media Library v0.3 — authorized, official embeddable video sources.
   No downloads, streaming extraction, DRM circumvention or proxying.
   Availability of external embeds varies by territory and uploader policy. */
const kadrOfficialMedia=[
 {id:"mos-operation",title:"Операция «Ы»",en:"Operation Y and Shurik's Other Adventures",year:1965,type:"Фильм",genre:["Комедия"],tag:"Полный фильм · RU",color:"linear-gradient(155deg,#9c8469,#55454d 62%,#211a27)",desc:"Три комедийные истории о приключениях Шурика. Полная версия от официального канала «Мосфильм».",source:"Киноконцерн «Мосфильм»",yt:"7ICl3sPny94",rt:"fcb693c38528938caa3b70101b7c58bf"},
 {id:"mos-pigeons",title:"Любовь и голуби",en:"Love and Pigeons",year:1984,type:"Фильм",genre:["Комедия","Драма"],tag:"Полный фильм · RU",color:"linear-gradient(145deg,#9d826c,#4d6764 64%,#201b26)",desc:"Тёплая комедия Владимира Меньшова. Полный фильм на официальном канале студии.",source:"Киноконцерн «Мосфильм»",yt:"0KmS5gk4ve4"},
 {id:"mos-diamond",title:"Бриллиантовая рука",en:"The Diamond Arm",year:1968,type:"Фильм",genre:["Комедия"],tag:"Полный фильм · RU",color:"linear-gradient(155deg,#9e9467,#5b5850 63%,#1e1e2a)",desc:"Классическая комедия Леонида Гайдая с Юрием Никулиным в главной роли. Видео с официального канала студии.",source:"Киноконцерн «Мосфильм»",yt:"B-iVfLX2tvY"},
 {id:"mos-office",title:"Служебный роман · 1 серия",en:"Office Romance, Part 1",year:1977,type:"Фильм",genre:["Комедия","Драма"],tag:"1-я серия · RU",color:"linear-gradient(145deg,#8d788f,#50455d 62%,#211c2b)",desc:"Первая серия фильма Эльдара Рязанова. Это именно первая часть, а не оба эпизода.",source:"Киноконцерн «Мосфильм»",yt:"hR-1QGMK75c",rt:"9b8a20607f7a35bde58aaf51fd9d8348"},
 {id:"mos-ivan",title:"Иван Васильевич меняет профессию",en:"Ivan Vasilievich Changes Profession",year:1973,type:"Фильм",genre:["Комедия","Фантастика"],tag:"Полный фильм · RU",color:"linear-gradient(145deg,#829784,#45646e 62%,#1c2230)",desc:"Машина времени соединяет эпохи в комедии Леонида Гайдая. Полный фильм на официальном канале «Мосфильма» в RuTube.",source:"Киноконцерн «Мосфильм»",rt:"de108b940b59e5e9d361077328ca9201"}
];
kadrOfficialMedia.forEach(f=>{if(!films.some(x=>x.id===f.id))films.push(f);if(f.yt)images[f.id]="https://i.ytimg.com/vi/"+f.yt+"/hqdefault.jpg"});
const kadrById=Object.fromEntries(kadrOfficialMedia.map(f=>[f.id,f]));
const kadrPreviousHome=home;
const kadrPreviousDetails=details;
const kadrPreviousPlayer=player;
let kadrChosenProvider={};
const kadrLastWatch={};
function kadrValidProvider(x){return kadrChosenProvider[x.id]==="rt"&&x.rt?"rt":x.yt?"yt":"rt"}
function kadrHostUrl(x,provider){
 if(provider==="yt"&&x.yt)return "https://www.youtube.com/watch?v="+x.yt;
 if(provider==="rt"&&x.rt)return "https://rutube.ru/video/"+x.rt+"/";
 return "";
}
function kadrEmbedUrl(x,provider){
 if(provider==="yt"&&x.yt)return "https://www.youtube-nocookie.com/embed/"+x.yt+"?playsinline=1&rel=0&hl=ru";
 if(provider==="rt"&&x.rt)return "https://rutube.ru/play/embed/"+x.rt+"/";
 return "";
}
function kadrSourceSection(){
 return '<section class="section kadr-official-section"><div class="section-heading"><div><h2>Смотреть прямо сейчас</h2><p>Полные русскоязычные фильмы от правообладателя</p></div><span class="kadr-free-label">БЕСПЛАТНО</span></div><div class="rail">'+kadrOfficialMedia.map(card).join("")+'</div><p class="kadr-provider-disclaimer">Официальные плееры «Мосфильма». Доступность воспроизведения зависит от Польши, YouTube/RuTube и разрешений правообладателя.</p></section>';
}
home=function(){const base=kadrPreviousHome();return base.replace("</section>","</section>"+kadrSourceSection())};
details=function(){
 const old=kadrPreviousDetails();if(!selected||!kadrById[selected])return old;
 const x=kadrById[selected];
 let s=old.replace(/<div class="fineprint">/,'<div class="kadr-source-status"><span class="kadr-source-indicator"></span>Видео опубликовано правообладателем · русская звуковая дорожка</div><div class="fineprint">');
 s=s.replace(/(<button class="primary" data-watch="[^"]+" data-episode="1">)[\s\S]*?(<\/button>)/,'$1'+I("play")+"Смотреть в KADR"+'$2');
 // Existing details() adds generic provider and local-file actions. Replace only its generic actions with official options.
 s=s.replace(/<div class="kadr-extra-actions">[\s\S]*?<\/div>/,
 '<div class="kadr-extra-actions"><button class="kadr-action kadr-action-feature" data-watch="'+x.id+'" data-episode="1">'+I("play")+'Запустить фильм</button><button class="kadr-action" data-legal-open="'+x.id+'">'+I("right")+'Официальный источник</button></div>');
 s=s.replace(/<div class="kadr-truth">[\s\S]*?<\/div>/,
 '<div class="kadr-truth"><span class="kadr-truth-dot"></span>Этот фильм доступен бесплатно на канале «Мосфильма». Если встраивание недоступно в твоём регионе, используй кнопку официального источника.</div>');
 return s;
};
player=function(){
 if(!playing||!kadrById[playing])return kadrPreviousPlayer();
 const x=kadrById[playing],p=kadrValidProvider(x);
 const url=kadrEmbedUrl(x,p);
 return '<div class="overlay kadr-official-overlay" id="player" role="dialog" aria-modal="true" aria-label="Воспроизведение фильма">'+
 '<div class="player kadr-official-player"><button class="player-back" data-back>'+I("back")+'К фильму</button>'+
 '<div class="kadr-player-heading"><h2>'+esc(x.title)+'</h2><div class="kadr-source-byline">Источник: '+esc(x.source)+' · русский язык</div></div>'+
 '<div class="kadr-player-frame kadr-official-frame">'+
 '<iframe id="kadr-official-iframe" title="'+esc(x.title)+'" src="'+esc(url)+'" loading="eager" referrerpolicy="strict-origin-when-cross-origin" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe>'+
 '</div><div class="kadr-player-tools">'+
 '<div class="kadr-player-provider">'+
 (x.yt?'<button class="'+(p==="yt"?"active":"")+'" data-stream-provider="yt">YouTube</button>':"")+
 (x.rt?'<button class="'+(p==="rt"?"active":"")+'" data-stream-provider="rt">RuTube</button>':"")+
 '</div><button class="kadr-source-open" data-legal-open="'+x.id+'">'+I("right")+'Открыть у правообладателя</button>'+
 '</div><p class="kadr-embed-note">Нажми воспроизведение внутри видео. Если плеер сообщает, что видео недоступно, попробуй другой источник или открой его у правообладателя. Мы не обходим региональные ограничения.</p>'+
 '</div></div>';
};
document.addEventListener("click",event=>{
 const b=event.target.closest("button");if(!b)return;
 if(b.hasAttribute("data-stream-provider")&&playing&&kadrById[playing]){
   event.preventDefault();event.stopImmediatePropagation();
   const x=kadrById[playing],p=b.dataset.streamProvider;
   if(p!=="yt"&&p!=="rt")return;
   if((p==="yt"&&!x.yt)||(p==="rt"&&!x.rt))return;
   kadrChosenProvider[x.id]=p;render();return;
 }
 if(b.hasAttribute("data-legal-open")){
   event.preventDefault();event.stopImmediatePropagation();
   const x=kadrById[b.dataset.legalOpen];if(!x)return;
   const link=kadrHostUrl(x,kadrValidProvider(x));
   if(link)window.location.href=link;
   return;
 }
 if(b.hasAttribute("data-watch")&&kadrById[b.dataset.watch]){
   event.preventDefault();event.stopImmediatePropagation();
   playing=b.dataset.watch;
   episode=1;
   render();
   return;
 }
},true);
render();