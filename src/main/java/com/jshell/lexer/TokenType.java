package com.jshell.lexer;

/**
 * The type of a lexer token.
 *
 * <p>Tokens are grouped into categories:
 * <ul>
 *   <li><b>Words</b>  – command names, arguments, variable names</li>
 *   <li><b>Operators</b>  – shell control and I/O operators</li>
 *   <li><b>Structural</b>  – newline, EOF</li>
 * </ul>
 */
public enum TokenType {

    // -----------------------------------------------------------------------
    // Words
    // -----------------------------------------------------------------------

    /** A plain word: command name, argument, variable value. */
    WORD,

    /** An assignment word, e.g. {@code FOO=bar}. */
    ASSIGNMENT_WORD,

    // -----------------------------------------------------------------------
    // I/O Redirect operators
    // -----------------------------------------------------------------------

    /** {@code >}  – redirect stdout (truncate) */
    REDIRECT_OUT,

    /** {@code >>}  – redirect stdout (append) */
    REDIRECT_APPEND,

    /** {@code <}  – redirect stdin */
    REDIRECT_IN,

    /** {@code <<}  – here document */
    HERE_DOC,

    /** {@code <<<}  – here string */
    HERE_STRING,

    /** {@code 2>}  – redirect stderr (truncate) */
    REDIRECT_ERR,

    /** {@code 2>>}  – redirect stderr (append) */
    REDIRECT_ERR_APPEND,

    /** {@code &>}  – redirect stdout+stderr (truncate) */
    REDIRECT_ALL,

    /** {@code &>>}  – redirect stdout+stderr (append) */
    REDIRECT_ALL_APPEND,

    /** {@code 2>&1}  – duplicate stderr to stdout */
    REDIRECT_ERR_TO_OUT,

    /** {@code 1>&2}  – duplicate stdout to stderr */
    REDIRECT_OUT_TO_ERR,

    /** {@code n>} where n is an explicit FD number (stored in the token's raw value) */
    REDIRECT_FD_OUT,

    /** {@code n<} where n is an explicit FD number */
    REDIRECT_FD_IN,

    // -----------------------------------------------------------------------
    // Control operators
    // -----------------------------------------------------------------------

    /** {@code |}  – pipe */
    PIPE,

    /** {@code ||}  – logical OR */
    OR,

    /** {@code &&}  – logical AND */
    AND,

    /** {@code ;}  – sequential command separator */
    SEMI,

    /** {@code &}  – background / asynchronous execution */
    AMP,

    /** {@code (}  – open subshell / command group */
    LPAREN,

    /** {@code )}  – close subshell / command group */
    RPAREN,

    /** <code>{</code>  – open brace group */
    LBRACE,

    /** <code>}</code>  – close brace group */
    RBRACE,

    /** {@code !}  – negation / history expansion prefix */
    BANG,

    // -----------------------------------------------------------------------
    // Structural
    // -----------------------------------------------------------------------

    /** A newline character (acts as command terminator, same as {@code ;} in many contexts). */
    NEWLINE,

    /** End of input. */
    EOF
}
