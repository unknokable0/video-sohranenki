import React, {useEffect, useRef, useState} from 'react';
import {ActivityIndicator, Alert, FlatList, Linking, Pressable, SafeAreaView, StatusBar, StyleSheet, Text, View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {WebView} from 'react-native-webview';

const CHANNEL='https://t.me/s/t2x2_video';
const KEY='saved_links_v1';
const C={bg:'#0b0911',panel:'#151120',panel2:'#1e1730',purple:'#8b5cf6',purple2:'#a78bfa',text:'#f7f5ff',muted:'#9e96ad',border:'#2a213c',danger:'#ff6b8a'};

function Btn({label,onPress,disabled,active}){
  return <Pressable disabled={disabled} onPress={onPress} style={({pressed})=>[s.btn,active&&s.btnActive,disabled&&{opacity:.35},pressed&&!disabled&&{opacity:.72}]}>
    <Text style={[s.btnText,active&&{color:'#fff'}]}>{label}</Text>
  </Pressable>;
}

export default function App(){
  const web=useRef(null);
  const [tab,setTab]=useState('videos');
  const [loading,setLoading]=useState(true);
  const [back,setBack]=useState(false);
  const [forward,setForward]=useState(false);
  const [url,setUrl]=useState(CHANNEL);
  const [saved,setSaved]=useState([]);

  useEffect(()=>{AsyncStorage.getItem(KEY).then(v=>v&&setSaved(JSON.parse(v))).catch(()=>{});},[]);
  const persist=async v=>{setSaved(v);await AsyncStorage.setItem(KEY,JSON.stringify(v));};

  const save=async()=>{
    if(!url.startsWith('https://t.me/')) return Alert.alert('Не удалось сохранить','Открой нужную запись в канале.');
    if(saved.some(x=>x.url===url)) return Alert.alert('Уже сохранено','Эта страница уже есть в сохранённых.');
    await persist([{url,createdAt:Date.now()},...saved]);
    Alert.alert('Сохранено','Добавлено в раздел «Сохранённые».');
  };

  const openSaved=(u)=>{
    setUrl(u);setTab('videos');
    setTimeout(()=>web.current?.injectJavaScript(`window.location.href=${JSON.stringify(u)};true;`),100);
  };

  const openTelegram=async()=>{
    const deep='tg://resolve?domain=t2x2_video', fallback='https://t.me/t2x2_video';
    try{await Linking.openURL(await Linking.canOpenURL(deep)?deep:fallback);}catch{Linking.openURL(fallback);}
  };

  return <SafeAreaView style={s.safe}>
    <StatusBar barStyle="light-content" backgroundColor={C.bg}/>
    <View style={s.header}>
      <View><Text style={s.title}>ВИДЕО СОХРАНЕНКИ</Text><Text style={s.sub}>@t2x2_video</Text></View>
      <Pressable style={s.telegram} onPress={openTelegram}><Text style={s.telegramText}>Telegram</Text></Pressable>
    </View>

    {tab==='videos'&&<>
      <View style={s.toolbar}>
        <Btn label="←" disabled={!back} onPress={()=>web.current?.goBack()}/>
        <Btn label="→" disabled={!forward} onPress={()=>web.current?.goForward()}/>
        <Btn label="Обновить" onPress={()=>web.current?.reload()}/>
        <Btn label="Сохранить" onPress={save}/>
      </View>
      <View style={s.webWrap}>
        {loading&&<View style={s.loader} pointerEvents="none"><ActivityIndicator size="large"/><Text style={s.loaderText}>Загрузка…</Text></View>}
        <WebView
          ref={web}
          source={{uri:url}}
          style={s.web}
          javaScriptEnabled
          domStorageEnabled
          mediaPlaybackRequiresUserAction={false}
          allowsFullscreenVideo
          allowsInlineMediaPlayback
          setSupportMultipleWindows={false}
          onLoadStart={()=>setLoading(true)}
          onLoadEnd={()=>setLoading(false)}
          onError={()=>setLoading(false)}
          onNavigationStateChange={n=>{setBack(n.canGoBack);setForward(n.canGoForward);if(n.url)setUrl(n.url);}}
          onShouldStartLoadWithRequest={r=>{if(r.url.startsWith('tg://')){Linking.openURL(r.url).catch(()=>{});return false;}return true;}}
        />
      </View>
    </>}

    {tab==='saved'&&<View style={s.page}>
      <Text style={s.pageTitle}>Сохранённые</Text>
      <Text style={s.hint}>Ссылки остаются после перезапуска приложения.</Text>
      {saved.length===0?<View style={s.card}><Text style={s.cardTitle}>Пока пусто</Text><Text style={s.cardText}>Открой запись и нажми «Сохранить».</Text></View>:
      <FlatList data={saved} keyExtractor={x=>x.url} contentContainerStyle={{paddingBottom:20}}
        renderItem={({item,index})=><View style={s.savedCard}>
          <Pressable style={{flex:1}} onPress={()=>openSaved(item.url)}>
            <Text style={s.cardTitle}>Запись #{saved.length-index}</Text>
            <Text style={s.savedUrl} numberOfLines={2}>{item.url}</Text>
          </Pressable>
          <Pressable style={s.remove} onPress={()=>persist(saved.filter(x=>x.url!==item.url))}><Text style={s.removeText}>Удалить</Text></Pressable>
        </View>}/>
      }
    </View>}

    {tab==='settings'&&<View style={s.page}>
      <Text style={s.pageTitle}>Настройки</Text>
      <View style={s.card}><Text style={s.cardTitle}>Источник</Text><Text style={s.cardText}>Публичный канал @t2x2_video</Text></View>
      <View style={s.card}><Text style={s.cardTitle}>Версия</Text><Text style={s.cardText}>1.0.0 · рабочая база</Text></View>
      <Pressable style={s.big} onPress={openTelegram}><Text style={s.bigText}>Открыть канал в Telegram</Text></Pressable>
    </View>}

    <View style={s.bottom}>
      <Btn label="Видео" active={tab==='videos'} onPress={()=>setTab('videos')}/>
      <Btn label="Сохранённые" active={tab==='saved'} onPress={()=>setTab('saved')}/>
      <Btn label="Настройки" active={tab==='settings'} onPress={()=>setTab('settings')}/>
    </View>
  </SafeAreaView>;
}

const s=StyleSheet.create({
  safe:{flex:1,backgroundColor:C.bg},
  header:{minHeight:72,paddingHorizontal:18,paddingVertical:12,flexDirection:'row',alignItems:'center',justifyContent:'space-between',borderBottomWidth:1,borderBottomColor:C.border,backgroundColor:C.bg},
  title:{color:C.text,fontSize:18,fontWeight:'800',letterSpacing:.3},
  sub:{color:C.muted,fontSize:12,marginTop:3},
  telegram:{backgroundColor:C.purple,paddingHorizontal:14,paddingVertical:9,borderRadius:12},
  telegramText:{color:'#fff',fontWeight:'800'},
  toolbar:{padding:8,gap:7,flexDirection:'row',backgroundColor:C.panel,borderBottomWidth:1,borderBottomColor:C.border},
  btn:{minHeight:38,paddingHorizontal:12,borderRadius:11,backgroundColor:C.panel2,alignItems:'center',justifyContent:'center',borderWidth:1,borderColor:C.border},
  btnActive:{backgroundColor:C.purple,borderColor:C.purple},
  btnText:{color:C.text,fontWeight:'700',fontSize:13},
  webWrap:{flex:1,backgroundColor:C.bg},
  web:{flex:1,backgroundColor:C.bg},
  loader:{...StyleSheet.absoluteFillObject,zIndex:10,alignItems:'center',justifyContent:'center',backgroundColor:C.bg},
  loaderText:{color:C.muted,marginTop:12},
  page:{flex:1,padding:16,backgroundColor:C.bg},
  pageTitle:{color:C.text,fontSize:24,fontWeight:'800',marginBottom:6},
  hint:{color:C.muted,marginBottom:16},
  card:{backgroundColor:C.panel,padding:16,borderRadius:16,borderWidth:1,borderColor:C.border,marginBottom:12},
  cardTitle:{color:C.text,fontWeight:'800',fontSize:15},
  cardText:{color:C.muted,marginTop:5},
  savedCard:{flexDirection:'row',gap:10,alignItems:'center',backgroundColor:C.panel,padding:14,borderRadius:16,borderWidth:1,borderColor:C.border,marginBottom:10},
  savedUrl:{color:C.muted,marginTop:5,fontSize:12},
  remove:{padding:10},
  removeText:{color:C.danger,fontWeight:'700'},
  big:{backgroundColor:C.purple,padding:15,borderRadius:14,alignItems:'center',marginTop:6},
  bigText:{color:'#fff',fontWeight:'800'},
  bottom:{flexDirection:'row',justifyContent:'space-around',gap:8,padding:10,backgroundColor:C.panel,borderTopWidth:1,borderTopColor:C.border}
});