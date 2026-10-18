package com.example.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small Markdown-to-HTML renderer.
 * <p>
 * Supported constructs:
 * <ul>
 *   <li>Fenced code blocks: {@code ```lang ... ```} -> {@code <pre><code>...</code></pre>}</li>
 *   <li>Inline code: {@code `code`} -> {@code <code>code</code>}</li>
 *   <li>ATX headings: {@code # Heading} .. {@code ###### Heading} -> {@code <h1>} .. {@code <h6>}</li>
 *   <li>Bold: {@code **bold**} -> {@code <strong>bold</strong>}</li>
 *   <li>Italic: {@code *italic*} -> {@code <em>italic</em>}</li>
 * </ul>
 * Plain text passes through unchanged.
 */
public class MarkdownRenderer {

    /**
     * Matches a fenced code block. The optional language tag after the opening
     * backticks is intentionally dropped; only the body is emitted.
     */
    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```[a-zA-Z0-9]*\\n(.*?)```", Pattern.DOTALL);

    /**
     * Renders the given Markdown source to an HTML fragment.
     *
     * @param markdown the Markdown source, may be {@code null}
     * @return the rendered HTML, or an empty string when {@code markdown} is {@code null}
     */
    public String render(String markdown) {
        if (markdown == null) {
            return "";
        }

        String result = renderCodeBlocks(markdown);
        result = renderHeadings(result);
        result = renderParagraphs(result);
        result = renderInlineCode(result);
        result = renderBold(result);
        result = renderItalic(result);

        return result;
    }

    private String renderCodeBlocks(String text) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1).trim();
            matcher.appendReplacement(
                    sb,
                    Matcher.quoteReplacement("<pre><code>" + content + "</code></pre>"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String renderInlineCode(String text) {
        return text.replaceAll("`([^`]+)`", "<code>$1</code>");
    }

    private String renderHeadings(String text) {
        for (int level = 1; level <= 6; level++) {
            String hashes = "#".repeat(level);
            text = text.replaceAll(
                    "(?m)^" + hashes + "\\s+(.+)$",
                    "<h" + level + ">$1</h" + level + ">");
        }
        return text;
    }

    /**
     * Wraps blocks separated by blank lines in {@code <p>} tags. A single
     * block (no blank-line separation) is left untouched, and blocks that
     * are already block-level elements (headings, code blocks) pass through
     * unchanged.
     */
    private String renderParagraphs(String text) {
        String[] blocks = text.split("\\n\\s*\\n");
        if (blocks.length < 2) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append('\n');
            }
            first = false;
            if (isBlockElement(trimmed)) {
                sb.append(trimmed);
            } else {
                sb.append("<p>").append(trimmed).append("</p>");
            }
        }
        return sb.toString();
    }

    private boolean isBlockElement(String block) {
        return block.startsWith("<pre>") || block.startsWith("<h");
    }

    private String renderBold(String text) {
        return text.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
    }

    private String renderItalic(String text) {
        return text.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
    }
}
