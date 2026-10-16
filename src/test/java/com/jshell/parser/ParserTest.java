package com.jshell.parser;

import com.jshell.lexer.Lexer;
import com.jshell.lexer.Token;
import com.jshell.parser.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Node parse(String input) {
        List<Token> tokens = new Lexer(input).tokenize();
        return new Parser(tokens).parse();
    }

    @Test
    void simpleCommand() {
        Node node = parse("echo hello world");
        assertInstanceOf(SimpleCommand.class, node);
        SimpleCommand cmd = (SimpleCommand) node;
        assertEquals("echo", cmd.name());
        assertEquals(List.of("hello", "world"), cmd.args());
    }

    @Test
    void emptyInput() {
        Node node = parse("   ");
        assertNull(node);
    }

    @Test
    void pipeline() {
        Node node = parse("ls | grep foo");
        assertInstanceOf(Pipeline.class, node);
        Pipeline pl = (Pipeline) node;
        assertEquals(2, pl.commands().size());
        assertFalse(pl.negate());
    }

    @Test
    void negatedPipeline() {
        Node node = parse("! false");
        // Single command with negation wraps in Pipeline
        assertInstanceOf(Pipeline.class, node);
        assertTrue(((Pipeline) node).negate());
    }

    @Test
    void andOr() {
        Node node = parse("make && make install");
        assertInstanceOf(AndOr.class, node);
        AndOr ao = (AndOr) node;
        assertEquals("&&", ao.operator());
    }

    @Test
    void commandList() {
        Node node = parse("cmd1; cmd2; cmd3");
        assertInstanceOf(CommandList.class, node);
        CommandList cl = (CommandList) node;
        assertEquals(3, cl.commands().size());
    }

    @Test
    void backgroundCommand() {
        Node node = parse("sleep 10 &");
        // Single background command is wrapped in CommandList
        assertInstanceOf(CommandList.class, node);
        CommandList cl = (CommandList) node;
        assertTrue(cl.separators().contains("&"));
    }

    @Test
    void subshell() {
        Node node = parse("(echo hi)");
        assertInstanceOf(Subshell.class, node);
    }

    @Test
    void groupCommand() {
        Node node = parse("{ echo hi; }");
        assertInstanceOf(GroupCommand.class, node);
    }

    @Test
    void redirection() {
        Node node = parse("echo hi > out.txt");
        assertInstanceOf(SimpleCommand.class, node);
        SimpleCommand cmd = (SimpleCommand) node;
        assertEquals(1, cmd.redirections().size());
        Redirection r = cmd.redirections().get(0);
        assertEquals("out.txt", r.target());
    }

    @Test
    void assignmentOnly() {
        Node node = parse("FOO=bar");
        assertInstanceOf(SimpleCommand.class, node);
        SimpleCommand cmd = (SimpleCommand) node;
        assertTrue(cmd.isAssignmentOnly());
        assertEquals("FOO", cmd.assignments().get(0).name());
        assertEquals("bar", cmd.assignments().get(0).value());
    }

    @Test
    void complexPipeline() {
        Node node = parse("cat file.txt | grep hello | wc -l");
        assertInstanceOf(Pipeline.class, node);
        assertEquals(3, ((Pipeline) node).commands().size());
    }

    @Test
    void syntaxErrorThrows() {
        assertThrows(ParseException.class, () -> parse("|"));
    }
}
