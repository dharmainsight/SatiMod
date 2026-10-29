package jp.flowview.android;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AddonCatalogTest {
    private JSONObject addon(String url) throws Exception {
        return new JSONObject().put("type","extension").put("name",new JSONObject().put("en-US","Test").put("ja","テスト"))
                .put("summary",new JSONObject().put("en-US","Useful"))
                .put("authors",new JSONArray().put(new JSONObject().put("name","Author")))
                .put("current_version",new JSONObject().put("compatibility",new JSONObject().put("android",new JSONObject().put("min","120.0")))
                        .put("file",new JSONObject().put("status","public").put("url",url)));
    }
    private String payload(JSONObject... values) throws Exception {return new JSONObject().put("results",new JSONArray(values)).put("next",JSONObject.NULL).toString();}
    @Test public void queryPreservesJapaneseAndCannotInjectParameters(){
        String url=AddonCatalog.searchUrl("  動画 &page=999  ",2);
        assertTrue(url.contains("app=android&appversion=158.0&type=extension"));
        assertTrue(url.contains("page_size=20&page=2&q=%E5%8B%95%E7%94%BB+%26page%3D999"));
        assertFalse(url.contains("&page=999"));
    }
    @Test public void validatesQueryBeforeNetworking(){
        for(String query:new String[]{"","   ","x".repeat(101)}){
            try{AddonCatalog.searchUrl(query,1);fail("Invalid query accepted");}catch(IllegalArgumentException expected){}
        }
        try{AddonCatalog.searchUrl("valid",0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void parsesLocalizedAndroidExtensionAndPagination() throws Exception {
        JSONObject entry=addon("https://addons.mozilla.org/firefox/downloads/file/123/test.xpi");
        String json=new JSONObject(payload(entry)).put("next","https://addons.mozilla.org/api/v5/addons/search/?page=2").toString();
        AddonCatalog.Result result=AddonCatalog.parse(json);
        assertEquals(1,result.addons.size());assertEquals("テスト",result.addons.get(0).name);
        assertEquals("Author",result.addons.get(0).author);assertEquals("Useful",result.addons.get(0).summary);assertTrue(result.hasMore);
    }
    @Test public void filtersDesktopOnlyUnpublishedAndNonExtensions() throws Exception {
        JSONObject desktop=addon("https://addons.mozilla.org/firefox/downloads/latest/test/latest.xpi");
        desktop.getJSONObject("current_version").getJSONObject("compatibility").remove("android");
        JSONObject unpublished=addon("https://addons.mozilla.org/firefox/downloads/latest/test/latest.xpi");
        unpublished.getJSONObject("current_version").getJSONObject("file").put("status","disabled");
        JSONObject theme=addon("https://addons.mozilla.org/firefox/downloads/latest/test/latest.xpi").put("type","statictheme");
        assertTrue(AddonCatalog.parse(payload(desktop,unpublished,theme)).addons.isEmpty());
    }
    @Test public void refusesUnsafeDownloadLocations() throws Exception {
        for(String url:new String[]{"http://addons.mozilla.org/firefox/downloads/test.xpi","https://addons.mozilla.org.evil.test/firefox/downloads/test.xpi",
                "https://user@addons.mozilla.org/firefox/downloads/test.xpi","javascript:alert(1)","https://addons.mozilla.org/firefox/downloads/test.exe",
                "https://addons.mozilla.org:8443/firefox/downloads/test.xpi","https://addons.mozilla.org/other/test.xpi"}){
            assertFalse(url,AddonCatalog.isDownloadUrl(url));assertTrue(AddonCatalog.parse(payload(addon(url))).addons.isEmpty());
        }
    }
    @Test public void emptyResultsAreDistinctFromMalformedResponse() throws Exception {
        AddonCatalog.Result empty=AddonCatalog.parse("{\"results\":[],\"next\":null}");assertTrue(empty.addons.isEmpty());assertFalse(empty.hasMore);
        try{AddonCatalog.parse("{\"error\":\"unavailable\"}");fail();}catch(org.json.JSONException expected){}
    }
    @Test public void nullJapaneseUsesActualDefaultTranslation() throws Exception {
        JSONObject value=new JSONObject("{\"ja\":null,\"en-GB\":\"Chord Transposer\",\"_default\":\"en-GB\"}");
        assertEquals("Chord Transposer",AddonCatalog.localized(value));
        value.put("ja","日本語");assertEquals("日本語",AddonCatalog.localized(value));
    }
    @Test public void nullAndMetadataAreNeverShownAsContent() throws Exception {
        assertEquals("",AddonCatalog.localized(JSONObject.NULL));assertEquals("",AddonCatalog.localized("null"));
        assertEquals("",AddonCatalog.localized(new JSONObject("{\"ja\":null,\"_default\":\"en-US\"}")));
        assertEquals("Français",AddonCatalog.localized(new JSONObject("{\"ja\":null,\"fr\":\"Français\"}")));
    }
}
