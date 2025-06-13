package org.zk.cpca.core;

import lombok.Data;
import lombok.ToString;

import java.util.*;

/**
 * @author zk
 */
public class AhoCorasickAutomaton {
    private final TrieNode root;

    public AhoCorasickAutomaton() {
        this.root = new TrieNode();
    }

    public void addPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return;
        }

        TrieNode current = root;
        for (char ch : pattern.toCharArray()) {
            current.children.putIfAbsent(ch, new TrieNode());
            current = current.children.get(ch);
        }
        current.isEndOfPattern = true;
        current.pattern = pattern;
    }

    public void buildFailurePointers() {
        Queue<TrieNode> queue = new LinkedList<>();

        // 根节点的所有直接子节点的失败指针指向根
        for (TrieNode firstLevelNode : root.children.values()) {
            if (firstLevelNode != null) {
                firstLevelNode.failurePointer = root;
                queue.add(firstLevelNode);
            }
        }

        // 为其余节点构建失败指针
        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();

            for (Map.Entry<Character, TrieNode> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                TrieNode child = entry.getValue();

                // 将子节点加入队列
                queue.add(child);

                // 从当前节点的失败指针开始查找
                TrieNode failureNode = current.failurePointer;
                while (failureNode != null && !failureNode.children.containsKey(ch)) {
                    failureNode = failureNode.failurePointer;
                }

                child.failurePointer = (failureNode == null) ? root : failureNode.children.get(ch);
            }
        }
    }

    public Set<String> search(String text) {
        Set<String> foundPatterns = new HashSet<>();
        TrieNode current = root;

        for (char ch : text.toCharArray()) {
            // 如果当前字符在子节点中找不到匹配，跟随失败指针继续查找
            while (current != root && !current.children.containsKey(ch)) {
                current = current.failurePointer;
            }

            // 如果找到匹配的子节点，移动到该节点
            if (current.children.containsKey(ch)) {
                current = current.children.get(ch);
            }

            // 检查是否到达某个模式的末尾
            TrieNode temp = current;
            while (temp != root) {
                if (temp.isEndOfPattern) {
                    foundPatterns.add(temp.pattern);
                }
                temp = temp.failurePointer;
            }
        }

        return foundPatterns;
    }

    /**
     * 查找文本中的所有匹配
     *
     * @param text 要搜索的文本
     * @return 匹配结果列表，包含匹配的词和位置信息
     */
    public List<MatchResult> findAll(String text) {
        List<MatchResult> results = new ArrayList<>();
        TrieNode current = root;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // 如果当前字符在子节点中找不到匹配，跟随失败指针继续查找
            while (current != root && !current.children.containsKey(ch)) {
                current = current.failurePointer;
            }

            // 如果找到匹配的子节点，移动到该节点
            if (current.children.containsKey(ch)) {
                current = current.children.get(ch);
            }

            // 检查是否到达某个模式的末尾
            TrieNode temp = current;
            while (temp != root) {
                if (temp.isEndOfPattern) {
                    results.add(new MatchResult(
                            temp.pattern,
                            i - temp.pattern.length() + 1,
                            i + 1
                    ));
                }
                temp = temp.failurePointer;
            }
        }

        return results;
    }

    private static class TrieNode {
        Map<Character, TrieNode> children;
        TrieNode failurePointer;
        boolean isEndOfPattern;
        String pattern;

        public TrieNode() {
            this.children = new HashMap<>();
            this.failurePointer = null;
            this.isEndOfPattern = false;
            this.pattern = null;
        }
    }

    @Data
    @ToString
    public static class MatchResult {
        private String keyword;   // 匹配的关键词
        private int start;        // 起始位置
        private int end;         // 结束位置

        public MatchResult(String keyword, int start, int end) {
            this.keyword = keyword;
            this.start = start;
            this.end = end;
        }
    }
}