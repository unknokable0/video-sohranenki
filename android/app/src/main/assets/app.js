"use strict";
// KADR 0.1 — interactive UI prototype. No video streams or licensed audio are included.
const films=[
{id:"mentalist",title:"Менталист",en:"The Mentalist",year:2008,type:"Сериал",seasons:7,genre:["Детектив","Драма"],tag:"Выбор недели",color:"linear-gradient(155deg,#83566c,#453653 57%,#181322)",desc:"Патрик Джейн помогает следователям распутывать сложные дела, используя наблюдательность и удивительное знание людей.",tv:"The Mentalist"},
{id:"interstellar",title:"Интерстеллар",en:"Interstellar",year:2014,type:"Фильм",genre:["Фантастика","Драма"],tag:"Культовый",color:"linear-gradient(145deg,#688c95,#3a5268 55%,#131d29)",desc:"Исследователи отправляются за пределы знакомого мира в поисках будущего для человечества."},
{id:"inception",title:"Начало",en:"Inception",year:2010,type:"Фильм",genre:["Фантастика","Триллер"],tag:"Топ",color:"linear-gradient(150deg,#637989,#3a445f 59%,#151b28)",desc:"Необычная команда берётся за почти невозможное задание в мире сновидений."},
{id:"edge",title:"Грань будущего",en:"Edge of Tomorrow",year:2014,type:"Фильм",genre:["Боевик","Фантастика"],tag:"Советуем",color:"linear-gradient(160deg,#9e6c53,#604253 57%,#1d1923)",desc:"Каждый новый день становится возможностью изменить исход невероятного противостояния."},
{id:"last",title:"Одни из нас",en:"The Last of Us",year:2023,type:"Сериал",seasons:2,genre:["Драма","Приключения"],tag:"Популярное",color:"linear-gradient(160deg,#7d8064,#414734 60%,#181e1b)",desc:"Путешествие через изменившийся мир, где доверие и человеческая связь значат всё.",tv:"The Last of Us"},
{id:"alice",title:"Алиса в Пограничье",en:"Alice in Borderland",year:2020,type:"Сериал",seasons:3,genre:["Триллер","Фантастика"],tag:"В тренде",color:"linear-gradient(160deg,#b07a83,#663c66 59%,#221526)",desc:"Загадочный Токио бросает героям вызов, и каждому решению приходится придавать значение.",tv:"Alice in Borderland"},
{id:"chernobyl",title:"Чернобыль",en:"Chernobyl",year:2019,type:"Мини-сериал",seasons:1,genre:["История","Драма"],tag:"Признанный",color:"linear-gradient(155deg,#929887,#5d6452 54%,#22241b)",desc:"История людей, столкнувшихся с последствиями трагедии и тяжёлыми решениями.",tv:"Chernobyl"},
{id:"breaking",title:"Во все тяжкие",en:"Breaking Bad",year:2008,type:"Сериал",seasons:5,genre:["Драма","Криминал"],tag:"Классика",color:"linear-gradient(155deg,#7c8c57,#4c633d 60%,#182416)",desc:"История человека, чья жизнь меняется после череды неожиданных решений.",tv:"Breaking Bad"},
{id:"severance",title:"Разделение",en:"Severance",year:2022,type:"Сериал",seasons:2,genre:["Триллер","Фантастика"],tag:"Загадка",color:"linear-gradient(150deg,#9bc1be,#4f8582 62%,#153134)",desc:"Сотрудники необычной компании начинают задавать вопросы о собственных воспоминаниях.",tv:"Severance"},
{id:"dark",title:"Тьма",en:"Dark",year:2017,type:"Сериал",seasons:3,genre:["Фантастика","Триллер"],tag:"Высокие оценки",color:"linear-gradient(155deg,#978c68,#5c5748 60%,#27221d)",desc:"Исчезновение ребёнка открывает тайны нескольких поколений небольшого города.",tv:"Dark"},
{id:"stranger",title:"Очень странные дела",en:"Stranger Things",year:2016,type:"Сериал",seasons:5,genre:["Фантастика","Драма"],tag:"Хит",color:"linear-gradient(155deg,#b35f64,#683642 60%,#24131e)",desc:"Компания друзей встречается с тайнами, которых никто не ожидал.",tv:"Stranger Things"},
{id:"dexter",title:"Декстер",en:"Dexter",year:2006,type:"Сериал",seasons:8,genre:["Детектив","Триллер"],tag:"Популярное",color:"linear-gradient(155deg,#ba7c78,#6c393e 57%,#28171b)",desc:"Напряжённый сериал о специалисте, ведущем сложную двойную жизнь.",tv:"Dexter"},
{id:"prison",title:"Побег",en:"Prison Break",year:2005,type:"Сериал",seasons:5,genre:["Триллер","Драма"],tag:"Классика",color:"linear-gradient(155deg,#af9972,#685945 60%,#292319)",desc:"Два брата, рискованный план и события, которые невозможно предсказать.",tv:"Prison Break"},
{id:"maze",title:"Бегущий в лабиринте",en:"The Maze Runner",year:2014,type:"Фильм",genre:["Фантастика","Приключения"],tag:"Приключение",color:"linear-gradient(150deg,#70988b,#3e6055 62%,#162a25)",desc:"Незнакомое место и группа друзей, ищущих ответы на множество вопросов."}
];
const glyph={
home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
catalog:'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M8 4v16M16 4v16M3 10h5M16 10h5"/>',
search:'<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
bookmark:'<path d="M6 4h12v17l-6-4-6 4z"/>',
check:'<path d="m4 12 5 5L20 6"/>',
user:'<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
play:'<path d="m8 4 12 8-12 8z" fill="currentColor" stroke="none"/>',
back:'<path d="m15 6-6 6 6 6"/>',
close:'<path d="M5 5 19 19M19 5 5 19"/>',
right:'<path d="m9 5 7 7-7 7"/>',
spark:'<path d="m12 2 1.6 7.4L21 11l-7.4 1.6L12 20l-1.6-7.4L3 11l7.4-1.6z"/>'
};
const I=n=>'<svg aria-hidden="true" viewBox="0 0 24 24">'+glyph[n]+'</svg>';
const esc=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
let tab="home",filter="Все",query="",selected=null,season=1,playing=null,episode=1;
const images={};
let saved=[];try{const s=JSON.parse(localStorage.getItem("kadr_favs")||"[]");if(Array.isArray(s))saved=s}catch(e){}
const app=document.getElementById("app");
const find=id=>films.find(x=>x.id===id);
function nav(k,label,icon,mobile){return '<button class="'+(tab===k?'active':'')+'" data-tab="'+k+'">'+I(icon)+(mobile?'<span>'+label+'</span>':label)+'</button>'}
function header(){return '<header class="top"><button class="logo" data-tab="home"><span class="logo-icon">'+I("catalog")+'</span><span>KADR</span><span class="prototype">DEMO</span></button><nav class="nav" aria-label="Разделы">'+nav("home","Главная","home")+nav("catalog","Каталог","catalog")+nav("search","Поиск","search")+nav("saved","Моё","bookmark")+'</nav><div class="utility"><button class="square" data-tab="search" aria-label="Поиск">'+I("search")+'</button><button class="avatar" data-tab="profile" aria-label="Профиль">K</button></div></header>'}
function footer(){return '<footer class="footer"><span>© KADR · интерактивный прототип, не сервис просмотра</span><span>Обложки телесериалов: TVmaze (при доступности API).</span></footer>'}
function bottom(){return '<nav class="bottom" aria-label="Основное меню">'+nav("home","Главная","home",true)+nav("catalog","Каталог","catalog",true)+nav("search","Поиск","search",true)+nav("saved","Моё","bookmark",true)+nav("profile","Профиль","user",true)+'</nav>'}
function poster(x){const src=images[x.id];return '<div class="cover'+(src?' has-image':'')+'" style="--grad:'+x.color+'">'+(src?'<img alt="" loading="lazy" src="'+esc(src)+'">':'')+'<span class="tag">'+esc(x.tag)+'</span><div class="cover-inner">'+(src?'':'<b>'+esc(x.title)+'</b><small>'+esc(x.en)+'</small>')+'</div></div>'}
function card(x){return '<button class="movie" data-open="'+x.id+'" aria-label="Открыть '+esc(x.title)+'">'+poster(x)+'<div class="movie-title">'+esc(x.title)+'</div><div class="movie-meta">'+x.year+' · '+x.type+'</div></button>'}
function rail(title,caption,list){return '<section class="section"><div class="section-heading"><div><h2>'+title+'</h2><p>'+caption+'</p></div><button class="more" data-tab="catalog">Все →</button></div><div class="rail">'+list.map(card).join("")+'</div></section>'}
function home(){return '<section class="hero"><div class="hero-art" aria-hidden="true">M</div><div class="hero-text"><div class="eyebrow">Сериал в центре внимания</div><h1>МЕНТА<span>ЛИСТ</span></h1><div class="meta"><span>2008</span><span>·</span><span>7 сезонов</span><span>·</span><span>Детектив / Драма</span></div><p>Знакомые герои, любимые истории и удобный просмотр. Посмотри, как будет выглядеть твой кинотеатр.</p><div class="btnrow"><button class="primary" data-open="mentalist">'+I("play")+'Открыть сериал</button><button class="secondary" data-save="mentalist">'+I(saved.includes("mentalist")?"check":"bookmark")+(saved.includes("mentalist")?"В моём":"В избранное")+'</button></div></div></section>'+rail("Для тебя","Популярное кино и сериалы",films.slice(0,7))+rail("Можно смотреть запоем","Истории на несколько вечеров",films.filter(x=>x.seasons).slice(2,11))+'<div class="info-banner">'+I("spark")+'<div><strong>Твой будущий кинотеатр — в одном приложении</strong><p>Пока это кликабельный макет. Настоящий каталог, русскую озвучку и видео нужно подключать отдельно.</p></div></div>'}
function match(){const q=query.trim().toLocaleLowerCase("ru");return films.filter(x=>(filter==="Все"||filter==="Фильмы"&&x.type==="Фильм"||filter==="Сериалы"&&x.type!=="Фильм"||x.genre.includes(filter))&&(!q||(x.title+" "+x.en+" "+x.genre.join(" ")).toLocaleLowerCase("ru").includes(q)))}
function grid(items){return items.length?'<div class="grid">'+items.map(card).join("")+'</div>':'<div class="empty"><strong>Ничего не нашлось</strong>Попробуй другое название или категорию.</div>'}
function chips(){return '<div class="filters">'+["Все","Сериалы","Фильмы","Детектив","Фантастика","Драма","Триллер","Приключения"].map(x=>'<button class="'+(filter===x?'active':'')+'" data-filter="'+x+'">'+x+'</button>').join("")+'</div>'}
function listing(search){return '<div class="page-head"><h1>'+(search?'Что будем смотреть?':'Каталог')+'</h1><p>'+(search?'Поиск по фильмам и сериалам':'Подборка для демонстрации будущего приложения')+'</p></div>'+(search?'<label class="search-field">'+I("search")+'<input id="search" aria-label="Название фильма или сериала" type="search" placeholder="Например, Менталист…" autocomplete="off" value="'+esc(query)+'"></label>':'')+chips()+'<div id="results">'+grid(match())+'</div>'}
function favs(){return '<div class="page-head"><h1>Моё</h1><p>Избранное сохраняется в браузере на этом устройстве</p></div>'+grid(films.filter(x=>saved.includes(x.id)))}
function profile(){return '<div class="page-head"><h1>Профиль</h1><p>KADR · версия прототипа 0.1</p></div><div class="info-banner">'+I("user")+'<div><strong>Привет! Это ещё не готовый онлайн-кинотеатр.</strong><p>Попробуй поиск, избранное и открытие серий. Видео и русская озвучка пока не подключены.</p></div></div>'+rail("Попробовать","Открой карточку сериала и выбери серию",films.slice(0,4))}
function details(){if(!selected)return "";const x=find(selected);if(!x)return "";const src=images[x.id];const episodes=x.seasons?'<div class="episodes-head"><h3>Серии</h3><select id="season-select" aria-label="Сезон">'+Array.from({length:x.seasons},(_,i)=>'<option value="'+(i+1)+'"'+(season===i+1?' selected':'')+'>Сезон '+(i+1)+'</option>').join("")+'</select></div>'+Array.from({length:5},(_,i)=>'<button class="episode" data-watch="'+x.id+'" data-episode="'+(i+1)+'"><span class="episode-art">'+I("play")+'</span><span><b>Серия '+(i+1)+'</b><small>Сезон '+season+' · Демонстрация плеера</small></span><span class="last">'+I("right")+'</span></button>').join(""):'<div class="episodes-head"><h3>Полный фильм</h3></div><p>Нажми кнопку просмотра выше, чтобы увидеть будущий видеоплеер.</p>';
return '<div class="overlay" id="details" role="dialog" aria-label="'+esc(x.title)+'" aria-modal="true"><article class="details"><div class="details-visual" style="--grad:'+x.color+'">'+(src?'<img alt="" src="'+esc(src)+'">':'')+'<button class="close" data-close aria-label="Закрыть">'+I("close")+'</button></div><div class="details-content"><div class="eyebrow">KADR / '+x.type+'</div><h2>'+esc(x.title)+'</h2><div class="details-meta">'+x.year+' · '+x.type+(x.seasons?' · '+x.seasons+' сезонов':'')+' · '+x.genre.join(' / ')+'</div><p>'+esc(x.desc)+'</p><div class="btnrow"><button class="primary" data-watch="'+x.id+'" data-episode="1">'+I("play")+(x.seasons?'Открыть серию':'Экран просмотра')+'</button><button class="secondary" data-save="'+x.id+'">'+I(saved.includes(x.id)?"check":"bookmark")+(saved.includes(x.id)?"В избранном":"В избранное")+'</button></div>'+episodes+'<div class="fineprint">Первые 5 серий каждого сезона показаны как пример интерфейса. Доступность фильмов и русской озвучки в Польше ещё не проверялась. Реального видео в прототипе нет.</div></div></article></div>'}
function player(){if(!playing)return "";const x=find(playing);return '<div class="overlay" id="player" role="dialog" aria-modal="true" aria-label="Демо плеера"><div class="player"><button class="player-back" data-back>'+I("back")+'Вернуться к фильму</button><div class="player-screen"><div><div class="play-symbol">'+I("play")+'</div><h2>Экран просмотра готов</h2><p>Пока это демонстрация. Видео появится здесь, когда подключим источник с правом на воспроизведение.</p></div></div><div class="player-meta"><span>'+esc(x.title)+' · '+(x.seasons?'Сезон '+season+', серия '+episode:'Фильм')+'</span><span>Русская озвучка: не проверена</span></div></div></div>'}
function render(){app.innerHTML='<div class="page">'+header()+'<main>'+(tab==="home"?home():tab==="catalog"?listing(false):tab==="search"?listing(true):tab==="saved"?favs():profile())+'</main>'+footer()+'</div>'+bottom()+details()+player();document.body.classList.toggle("lock",!!(selected||playing))}
function switchTab(k){tab=k;filter="Все";query="";selected=null;playing=null;render();window.scrollTo(0,0);if(k==="search"){const i=document.getElementById("search");if(i)i.focus()}}
function toggle(id){saved=saved.includes(id)?saved.filter(x=>x!==id):saved.concat(id);try{localStorage.setItem("kadr_favs",JSON.stringify(saved))}catch(e){}render()}
function close(){if(playing)playing=null;else selected=null;render()}
document.addEventListener("click",e=>{const b=e.target.closest("button");if(!b){if(e.target.classList.contains("overlay"))close();return}
if(b.dataset.tab){switchTab(b.dataset.tab);return}
if(b.dataset.open){selected=b.dataset.open;season=1;render();return}
if(b.dataset.save){toggle(b.dataset.save);return}
if(b.dataset.close!==undefined){close();return}
if(b.dataset.filter){filter=b.dataset.filter;const q=document.activeElement&&document.activeElement.id==="search";render();if(q)document.getElementById("search").focus();return}
if(b.dataset.watch){playing=b.dataset.watch;episode=Number(b.dataset.episode)||1;render();return}
if(b.dataset.back!==undefined){playing=null;render();return}});
document.addEventListener("input",e=>{if(e.target.id==="search"){query=e.target.value;document.getElementById("results").innerHTML=grid(match())}});
document.addEventListener("change",e=>{if(e.target.id==="season-select"){season=Number(e.target.value)||1;render()}});
document.addEventListener("keydown",e=>{if(e.key==="Escape"&&(selected||playing))close()});
render();
// Optional TVmaze cover artwork; CSS-only posters remain available if network is blocked.
async function getPoster(x){
 if(!x.tv)return;
 try{
  const controller=new AbortController();
  const timeout=setTimeout(()=>controller.abort(),4500);
  const res=await fetch("https://api.tvmaze.com/singlesearch/shows?q="+encodeURIComponent(x.tv),{signal:controller.signal});
  clearTimeout(timeout);
  if(!res.ok)return;
  const data=await res.json();
  const url=data.image&&(data.image.medium||data.image.original);
  if(typeof url==="string"&&url.startsWith("https://static.tvmaze.com/")){images[x.id]=url;if(document.activeElement?.id!=="search")render()}
 }catch(e){}
}
films.filter(x=>x.tv).forEach((x,i)=>setTimeout(()=>getPoster(x),i*450));