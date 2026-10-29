package jp.flowview.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Public Mozilla catalog only; installation and permission checks remain in GeckoView. */
final class AddonCatalog {
    static final int PAGE_SIZE=20;
    static final class Addon {
        final String name, author, summary, downloadUrl;
        Addon(String name,String author,String summary,String downloadUrl){this.name=name;this.author=author;this.summary=summary;this.downloadUrl=downloadUrl;}
    }
    static final class Result {
        final List<Addon> addons;
        final boolean hasMore;
        Result(List<Addon> addons,boolean hasMore){this.addons=addons;this.hasMore=hasMore;}
    }
    static String searchUrl(String query,int page){
        String q=query==null?"":query.trim();
        if(q.isEmpty()||q.codePointCount(0,q.length())>100||page<1)throw new IllegalArgumentException("検索語は1〜100文字で入力してください");
        try{return "https://addons.mozilla.org/api/v5/addons/search/?app=android&appversion=158.0&type=extension&lang=ja&page_size="+PAGE_SIZE+"&page="+page+"&q="+URLEncoder.encode(q,"UTF-8");}
        catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
    static boolean isDownloadUrl(String value){
        try{URI u=URI.create(value);return "https".equals(u.getScheme())&&"addons.mozilla.org".equals(u.getHost())
                &&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)
                &&u.getPath().startsWith("/firefox/downloads/")&&u.getPath().endsWith(".xpi");}
        catch(Exception e){return false;}
    }
    private static String text(Object value){
        if(!(value instanceof String))return "";
        String result=((String)value).trim();return "null".equalsIgnoreCase(result)?"":result;
    }
    static String localized(Object value){
        if(!(value instanceof JSONObject))return text(value);
        JSONObject translations=(JSONObject)value;
        for(String key:new String[]{"ja","ja-JP",text(translations.opt("_default")),"en-US","en-GB"}){
            if(key.isEmpty())continue;String found=text(translations.opt(key));if(!found.isEmpty())return found;
        }
        Iterator<String> keys=translations.keys();
        while(keys.hasNext()){String key=keys.next();if(key.startsWith("_"))continue;String found=text(translations.opt(key));if(!found.isEmpty())return found;}
        return "";
    }
    static Result parse(String json) throws org.json.JSONException {
        JSONObject response=new JSONObject(json);JSONArray results=response.getJSONArray("results");List<Addon> items=new ArrayList<>();
        for(int i=0;i<results.length();i++){
            JSONObject item=results.optJSONObject(i);if(item==null||!"extension".equals(item.optString("type")))continue;
            JSONObject version=item.optJSONObject("current_version");if(version==null)continue;
            JSONObject compatibility=version.optJSONObject("compatibility"),file=version.optJSONObject("file");
            if(compatibility==null||compatibility.optJSONObject("android")==null||file==null||!"public".equals(file.optString("status")))continue;
            String url=file.optString("url",""),name=localized(item.opt("name"));if(!isDownloadUrl(url)||name.isEmpty())continue;
            JSONArray authors=item.optJSONArray("authors");String author="";
            if(authors!=null&&authors.length()>0&&authors.optJSONObject(0)!=null)author=text(authors.optJSONObject(0).opt("name"));
            items.add(new Addon(name,author,localized(item.opt("summary")),url));
        }
        return new Result(items,!response.isNull("next")&&!response.optString("next","").isEmpty());
    }
    static Result search(String query,int page) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(searchUrl(query,page)).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","SatiMod/0.3.2 (Android; extension search)");
        try{
            int status=connection.getResponseCode();if(status!=200)throw new IOException(status==429?"検索が混み合っています。少し待って再検索してください":"検索サービスに接続できません（HTTP "+status+"）");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[16384];int n;
                while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("検索を中止しました");if(out.size()+n>2*1024*1024)throw new IOException("検索結果が大きすぎます。検索語を絞ってください");out.write(buffer,0,n);}
                return parse(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
}
