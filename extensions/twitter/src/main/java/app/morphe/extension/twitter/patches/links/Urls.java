/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.links;

import com.twitter.model.json.core.JsonUrlEntity;

import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.crimera.PikoUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Urls {
    private static final Pattern POST_URL_PATTERN = Pattern.compile(
            "^(https://(?:x\\.com|twitter\\.com)/)[^/]+(/status/.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final boolean unShortUrl;
    static {
        unShortUrl = SettingsStatus.unshortenlink && Pref.unShortUrl();
    }
    public static JsonUrlEntity unshort(JsonUrlEntity entity) {
        try {
            if(unShortUrl){
                entity.e = entity.c;
            }
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
        return entity;
    }

    public static String changeDomain(String urlString) {
        try {
            String customDomainName = Pref.customSharingDomain();
            if(customDomainName.length()<1) return urlString;

            // Check for domain extension
            String regex  = "(?:https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?:/[^\\s]*)?";
            if(!(customDomainName.matches(regex))) {
                //have .com as default extension just for safety reasons
                customDomainName += ".com";
            }
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")) {
                return new URL(url.getProtocol(), customDomainName, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception ex) {
            PikoUtils.logger(ex);
            return urlString;
        }
        return urlString;
    }

    /**
     * 12.46.1: X's R8 obfuscated away the `com.x.models.ContextualPost` class name.
     * The object passed here is `com.x.models.r1` (its toString still says
     * "ContextualPost(canonicalPost=...)") taken from ShareTarget.d. Resolve the
     * obfuscated author screen name reflectively:
     *   r1.b (u0 canonicalPost) -> u0.f (mh author) -> mh.D() (screen name)
     * If any step fails we return null; custom sharing domain still works, only the
     * legacy username rewrite is skipped. No feature is deliberately disabled.
     */
    private static String obfuscatedScreenName(Object contextualPost) {
        if (contextualPost == null) return null;
        try {
            Field canonicalField = contextualPost.getClass().getDeclaredField("b");
            canonicalField.setAccessible(true);
            Object canonical = canonicalField.get(contextualPost);
            if (canonical == null) return null;

            Field authorField = canonical.getClass().getDeclaredField("f");
            authorField.setAccessible(true);
            Object author = authorField.get(canonical);
            if (author == null) return null;

            Method screenNameMethod = author.getClass().getMethod("D");
            screenNameMethod.setAccessible(true);
            Object name = screenNameMethod.invoke(author);
            return name instanceof String ? (String) name : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String hookShareSheetLink(Object contextualPost, String link){
        if (link == null) return null;
        try {
            if (SettingsStatus.legacyShareLink) {
                Matcher matcher = POST_URL_PATTERN.matcher(link);
                if (matcher.matches()) {
                    String username = obfuscatedScreenName(contextualPost);
                    if (username != null && username.length() > 0) {
                        link = matcher.group(1) + username + matcher.group(2);
                    }
                }
            }
        }catch (Exception ex) {
            PikoUtils.logger(ex);
        }
        return changeDomain(link);
    }
}
