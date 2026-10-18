package com.example.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单元测试：MarkdownRenderer
 * 覆盖：标题 / 加粗 / 斜体 / 行内代码 / 代码块 / 纯文本透传
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersHeading() {
        assertEquals("<h1>Title</h1>", renderer.render("# Title"));
        assertEquals("<h2>Subtitle</h2>", renderer.render("## Subtitle"));
        assertEquals("<h3>Section</h3>", renderer.render("### Section"));
    }

    @Test
    void rendersBold() {
        assertEquals("<strong>bold</strong>", renderer.render("**bold**"));
        assertEquals("a <strong>b</strong> c", renderer.render("a **b** c"));
    }

    @Test
    void rendersItalic() {
        assertEquals("<em>italic</em>", renderer.render("*italic*"));
        assertEquals("a <em>b</em> c", renderer.render("a *b* c"));
    }

    @Test
    void rendersInlineCode() {
        assertEquals("<code>foo()</code>", renderer.render("`foo()`"));
        assertEquals("use <code>x</code> here", renderer.render("use `x` here"));
    }

    @Test
    void rendersCodeBlock() {
        String md = "```\nint a = 1;\n```";
        assertEquals("<pre><code>int a = 1;</code></pre>", renderer.render(md));
    }

    @Test
    void rendersBlankLineSeparatedParagraphs() {
        assertEquals("<p>First</p>\n<p>Second</p>", renderer.render("First\n\nSecond"));
        assertEquals("<p>A</p>\n<p>B</p>\n<p>C</p>", renderer.render("A\n\nB\n\nC"));
    }

    @Test
    void singleParagraphStaysPlain() {
        assertEquals("line1\nline2", renderer.render("line1\nline2"));
    }

    @Test
    void plainTextPassesThrough() {
        assertEquals("hello world", renderer.render("hello world"));
    }
}