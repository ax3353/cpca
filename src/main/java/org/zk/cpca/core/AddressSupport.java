package org.zk.cpca.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zk
 */
public class AddressSupport {

    private static final Pattern ABBR_MATCH_PATTERN;

    static {
        // 构建省/市级简称正则
        StringBuilder sb = new StringBuilder();
        for (String abbr : ShortNameDict.getAbbrMap().keySet()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(Pattern.quote(abbr));
        }
        ABBR_MATCH_PATTERN = Pattern.compile(sb.toString());
    }

    /**
     * 将地址中的简称替换为全名
     */
    public static String toFullName(String raw) {
        Matcher matcher = ABBR_MATCH_PATTERN.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String full = ShortNameDict.toFullName(match);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(full));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
