/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.entity;

import app.morphe.extension.twitter.entity.ExtMediaEntities;
import app.morphe.extension.twitter.entity.TweetInfo;
import app.morphe.extension.twitter.entity.Debug;

import java.util.*;
import app.morphe.extension.crimera.PikoUtils;

// All comments based of 11.14.beta-0
// Lcom/twitter/model/core/entity/e;
public class Tweet extends Debug {
    private final Object obj;

    public Tweet(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public Long getTweetId() throws Exception {
        return (Long) super.getMethod("getId");
    }

    // 2026-11-26：12.46.1 作者方法已從 u0(CanonicalPost) 上移到 author 物件
    // （ni 介面，f5/h8/rg 實作）。getDeclaredMethod 只在物件自身 class 上找，
    // 直接在 u0 上找 D()/getName() 會拋 NoSuchMethodException。改兩段式：
    // u0.d() 拿 author，再在 author 上反射（sentinel 皆由 patch 動態寫入）。
    public String getTweetUsername() throws Exception {
        Object author = super.getMethod("authorAccessorMethod");
        return (String) super.getMethod(author, "userNameMethod");
    }

    public String getTweetLink() throws Exception {
        Long tweetId = getTweetId();
        String username = getTweetUsername();   // 必須是無 @ 前綴的 D()，不能用 g()
        return "https://x.com/"+username+"/status/"+tweetId;
    }

    public String getTweetProfileName() throws Exception {
        Object author = super.getMethod("authorAccessorMethod");
        return (String) super.getMethod(author, "profileNameMethod");
    }

    public Long getTweetUserId() throws Exception {

        return (Long) super.getMethod("userIdMethod");
    }

    private ArrayList<ExtMediaEntities> getExtendedMediaEntities() throws Exception {
        ArrayList<ExtMediaEntities> extMediaEntitiesArrayList = new ArrayList();

        // 2026-11-28：12.46.1 的 u0.g() 直接回傳 Lkotlinx/collections/immutable/c;，
        // 那是個 extends java.util.List 的 interface。舊的「二段式 superclass +
        // extMediaList 欄位」反射（舊 c0→s→a:List）已物理失效。
        // 注意：下面兩個字串常數必須留在方法裡——patch 端的
        // changeFirstString(mediaMethod) 與 changeStringAt(1, extMediaListField)
        // 仍然會寫入這兩個槽位；把它們刪掉會讓 patch 找不到注入點。
        Object mediaRootObject = super.getMethod("mediaMethod");
        List<?> list = (List<?>) mediaRootObject;
        if (list == null) {
            throw new IllegalStateException("extMediaList is null");
        }

        list.forEach(item ->{
            extMediaEntitiesArrayList.add(new ExtMediaEntities(item));
        });

        return extMediaEntitiesArrayList;
    }

    public ArrayList<ArrayList<Media>> getMediaList() throws Exception {
        ArrayList<ArrayList<Media>> mediaData = new ArrayList();

        ArrayList<ExtMediaEntities> extMediaEntitiesArrayList = this.getExtendedMediaEntities();

        extMediaEntitiesArrayList.forEach(item ->{
                mediaData.add(item.getMediaList());
        });
        return mediaData;
    }

    public TweetInfo getTweetInfo() throws Exception {
        // 2026-11-27：12.46.1 已移除舊 TweetInfo object（com.twitter.database.legacy.tdbh），
        // language 直接是 CanonicalPost(u0) 自己的 String field。改為直接把 u0 包進
        // TweetInfo，getLang() 從 u0 讀由 patch 動態注入的混淆字段名（sentinel "tweetLang"）。
        return new TweetInfo(this.obj);
    }

    public String getTweetLang() throws Exception {
        TweetInfo tweetInfo = this.getTweetInfo();
        return tweetInfo.getLang();
    }

    public String getLongText() throws Exception {
        // j()Lcom/twitter/model/notetweet/b;
        Object noteTweetObj = super.getMethod("noteTweetMethod");
        String data = noteTweetObj != null ? (String) super.getField(noteTweetObj, "longTextField") : null;
        return data;
    }

    public String getShortText() throws Exception {
        // 12.46.1: CanonicalPost (com.x.models.u0) exposes getText()->String directly;
        // the old intermediate text-object class is gone. The patch injects the real
        // getter name ("getText") into the sentinel below, and we call it straight on
        // the tweet object (one-stage) instead of getText-on-a-text-object (two-stage).
        Object data = super.getMethod("tweetEntityClass");
        return (String) data;
    }

    public String getText() throws Exception {
        String text = "";
        try {
            text = this.getLongText();
            if (text == null) {
                text = this.getShortText();
            }
            // Replaces text to empty if the text contains only media link.
            text = text.replaceAll("pic\\.x\\.com/\\S+", "");
            // Replaces links.
            text = text.replaceAll("https?://t\\.co/\\S+", "");

        } catch (Exception e) {
            PikoUtils.logger(e);
            text = e.getMessage();
        }
        return text;

    }

    @Override
    public String toString() {
        try {
            return "Tweet [getTweetId()=" + this.getTweetId() + ", getTweetUsername()=" + this.getTweetUsername()
                    + ", getTweetProfileName()=" + this.getTweetProfileName() + ", getTweetUserId()=" + this.getTweetUserId()
                    + ", getMediaList()=" + this.getMediaList() + ", getTweetInfo()=" + this.getTweetInfo() + ", getTweetLang()="
                    + this.getTweetLang() + ", getLongText()=" + this.getLongText() + ", getShortText()=" + this.getShortText() + "]";

        } catch (Exception e) {
            PikoUtils.logger(e);
            return e.getMessage();
        }

    }
}
