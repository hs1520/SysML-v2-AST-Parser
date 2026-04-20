grammar SysMLv2;

// ============================================================
// PARSER RULES
// ============================================================

rootNamespace
    : member* EOF
    ;

member
    : visibility? memberElement
    ;

memberElement
    : packageDeclaration
    | namespaceDeclaration
    | interfaceDeclaration
    | systemDefDeclaration
    | partDefCompactDeclaration
    | dataTypeDeclaration
    | partDefDeclaration
    | blockDeclaration
    | partUsageDeclaration
    | componentUsageDeclaration
    | requirementDefDeclaration
    | requirementUsageDeclaration
    | portDefDeclaration
    | portUsageDeclaration
    | attributeDefDeclaration
    | attributeUsageDeclaration
    | actionDefDeclaration
    | actionUsageDeclaration
    | connectorDeclaration
    | satisfyDeclaration
    | refineDeclaration
    | importDeclaration
    | dependencyDeclaration
    | commentDeclaration
    | docDeclaration
    | metadataDeclaration
    | stateBehaviorDeclaration
    | directedFeatureDeclaration
    | constraintDeclaration
    | specializationDeclaration
    ;

visibility
    : PUBLIC
    | PRIVATE
    | PROTECTED
    ;

packageDeclaration
    : PACKAGE name LBRACE member* RBRACE
    ;

interfaceDeclaration
    : INTERFACE name LBRACE member* RBRACE
    ;

systemDefDeclaration
    : SYSTEMDEF name LBRACE member* RBRACE
    ;

partDefCompactDeclaration
    : PARTDEF name specializationClause? LBRACE member* RBRACE
    ;

dataTypeDeclaration
    : DATATYPE name LBRACE member* RBRACE
    ;

namespaceDeclaration
    : NAMESPACE name LBRACE member* RBRACE
    ;

partDefDeclaration
    : PART DEF name specializationClause? LBRACE member* RBRACE
    ;

blockDeclaration
    : BLOCK name specializationClause? LBRACE member* RBRACE
    ;

partUsageDeclaration
    : PART name typeClause? multiplicityClause? LBRACE member* RBRACE
    | PART name typeClause? multiplicityClause? SEMI
    ;

componentUsageDeclaration
    : COMPONENT name typeClause? LBRACE member* RBRACE
    | COMPONENT name typeClause? SEMI
    ;

requirementDefDeclaration
    : REQUIREMENT DEF name specializationClause? LBRACE requirementBody* RBRACE
    ;

requirementUsageDeclaration
    : REQUIREMENT name typeClause? LBRACE requirementBody* RBRACE
    | REQUIREMENT name typeClause? SEMI
    ;

requirementBody
    : subjectClause
    | requireClause
    | member
    ;

subjectClause
    : SUBJECT name typeClause? SEMI
    ;

requireClause
    : REQUIRE constraintDeclaration
    ;

portDefDeclaration
    : PORT DEF name specializationClause? LBRACE member* RBRACE
    ;

portUsageDeclaration
    : direction? PORT name typeClause? SEMI
    | direction? PORT name typeClause? LBRACE member* RBRACE
    | PORT name COLON direction qualifiedName SEMI
    ;

attributeDefDeclaration
    : ATTRIBUTE DEF name specializationClause? LBRACE member* RBRACE
    ;

attributeUsageDeclaration
    : direction? ATTRIBUTE name typeClause? (ASSIGN expression)? SEMI
    ;

actionDefDeclaration
    : ACTION DEF name actionSignature? specializationClause? LBRACE member* RBRACE
    ;

actionUsageDeclaration
    : ACTION name actionSignature? typeClause? SEMI
    | ACTION name actionSignature? typeClause? LBRACE member* RBRACE
    ;

actionSignature
    : LPAREN RPAREN
    ;

connectorDeclaration
    : CONNECT featurePath TO featurePath SEMI
    | CONNECTOR name? typeClause? CONNECT featurePath TO featurePath SEMI
    ;

featurePath
    : name (DOT name)*
    ;

satisfyDeclaration
    : SATISFY qualifiedName (COMMA qualifiedName)* (BY qualifiedName)? SEMI
    ;

refineDeclaration
    : REF? REFINE qualifiedName SEMI
    ;

specializationClause
    : SPECIALIZES qualifiedName (COMMA qualifiedName)*
    | SUBSETS qualifiedName (COMMA qualifiedName)*
    | COLON_GT qualifiedName (COMMA qualifiedName)*
    ;

constraintDeclaration
    : CONSTRAINT name? LBRACE expression RBRACE
    ;

importDeclaration
    : visibility? IMPORT qualifiedNameWithStar SEMI
    ;

dependencyDeclaration
    : DEPENDENCY name? FROM qualifiedName TO qualifiedName SEMI
    ;

commentDeclaration
    : COMMENT (ABOUT qualifiedName)? LBRACE commentText RBRACE
    ;

commentText
    : BLOCK_COMMENT_TEXT
    | STRING_LITERAL
    ;

docDeclaration
    : DOC (BLOCK_COMMENT_TEXT | STRING_LITERAL) SEMI?
    ;

directedFeatureDeclaration
    : direction name typeClause SEMI
    ;

stateBehaviorDeclaration
    : STATE BEHAVIOR LBRACE stateDeclaration* RBRACE
    ;

stateDeclaration
    : STATE name LBRACE stateStatement* RBRACE
    ;

stateStatement
    : ACCEPT name SEMI
    | DO invocation SEMI
    | SEND name SEMI
    | IF expression THEN conditionalAction SEMI
    ;

invocation
    : name LPAREN argumentList? RPAREN
    ;

conditionalAction
    : SEND name
    | invocation
    ;

metadataDeclaration
    : METADATA HASH name LBRACE member* RBRACE
    | METADATA HASH name SEMI
    ;

typeClause
    : COLON qualifiedName
    | COLON TILDE qualifiedName
    ;

multiplicityClause
    : LBRACKET multiplicityRange RBRACKET
    ;

multiplicityRange
    : expression
    | expression DOTDOT expression
    | STAR
    ;

direction
    : IN
    | OUT
    | INOUT
    ;

qualifiedName
    : name (DOUBLE_COLON name)* genericArguments?
    ;

genericArguments
    : LT qualifiedName (COMMA qualifiedName)* GT
    ;

qualifiedNameWithStar
    : qualifiedName (DOUBLE_COLON STAR)?
    | STAR
    ;

name
    : ID
    | UNRESTRICTED_NAME
    | keywordAsName
    ;

keywordAsName
    : PACKAGE | NAMESPACE | PART | DEF | BLOCK | REQUIREMENT
    | PORT | ATTRIBUTE | ACTION | CONNECT | CONNECTOR
    | SATISFY | REFINE | SPECIALIZES | SUBSETS | IMPORT
    | DEPENDENCY | COMMENT | DOC | METADATA | CONSTRAINT
    | SUBJECT | REQUIRE | FROM | TO | BY | ABOUT | REF
    | COMPONENT | IN | OUT | INOUT | PUBLIC | PRIVATE | PROTECTED
    ;

expression
    : primary
    | expression op=(STAR | SLASH) expression
    | expression op=(PLUS | MINUS) expression
    | expression op=(LT | GT | LE | GE | EQ | NEQ) expression
    | expression op=(AND | OR) expression
    | NOT expression
    | expression DOT name
    ;

primary
    : literal unitSuffix?
    | qualifiedName
    | LPAREN expression RPAREN
    | name LPAREN argumentList? RPAREN
    ;

unitSuffix
    : LBRACKET unitExpression RBRACKET
    ;

unitExpression
    : name (SLASH name)?
    | PERCENT
    ;

argumentList
    : expression (COMMA expression)*
    ;

literal
    : INTEGER_LITERAL
    | REAL_LITERAL
    | STRING_LITERAL
    | BOOLEAN_LITERAL
    | NULL_LITERAL
    ;

specializationDeclaration
    : specializationClause SEMI
    ;

// ============================================================
// LEXER RULES
// ============================================================

// Keywords
PACKAGE     : 'package' ;
NAMESPACE   : 'namespace' ;
PART        : 'part' ;
DEF         : 'def' ;
BLOCK       : 'block' ;
REQUIREMENT : 'requirement' ;
PORT        : 'port' ;
ATTRIBUTE   : 'attribute' ;
ACTION      : 'action' ;
CONNECT     : 'connect' ;
CONNECTOR   : 'connector' ;
INTERFACE   : 'interface' ;
SYSTEMDEF   : 'systemdef' ;
PARTDEF     : 'partdef' ;
DATATYPE    : 'datatype' ;
SATISFY     : 'satisfy' ;
REFINE      : 'refine' ;
SPECIALIZES : 'specializes' ;
SUBSETS     : 'subsets' ;
IMPORT      : 'import' ;
DEPENDENCY  : 'dependency' ;
COMMENT     : 'comment' ;
DOC         : 'doc' ;
METADATA    : 'metadata' ;
CONSTRAINT  : 'constraint' ;
SUBJECT     : 'subject' ;
REQUIRE     : 'require' ;
FROM        : 'from' ;
TO          : 'to' ;
BY          : 'by' ;
ABOUT       : 'about' ;
REF         : 'ref' ;
COMPONENT   : 'component' ;
STATE       : 'state' ;
BEHAVIOR    : 'behavior' ;
ACCEPT      : 'accept' ;
DO          : 'do' ;
SEND        : 'send' ;
IF          : 'if' ;
THEN        : 'then' ;
IN          : 'in' ;
OUT         : 'out' ;
INOUT       : 'inout' ;
PUBLIC      : 'public' ;
PRIVATE     : 'private' ;
PROTECTED   : 'protected' ;
AND         : 'and' | '&&' ;
OR          : 'or' | '||' ;
NOT         : 'not' | '!' ;
NULL_LITERAL : 'null' ;
BOOLEAN_LITERAL : 'true' | 'false' ;

// Symbols
LBRACE      : '{' ;
RBRACE      : '}' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
SEMI        : ';' ;
COMMA       : ',' ;
DOTDOT      : '..' ;
DOT         : '.' ;
ASSIGN      : '=' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
LE          : '<=' ;
GE          : '>=' ;
EQ          : '==' ;
NEQ         : '!=' ;
LT          : '<' ;
GT          : '>' ;
HASH        : '#' ;
PERCENT     : '%' ;
TILDE       : '~' ;
DOUBLE_COLON : '::' ;
COLON_GT    : ':>' ;
COLON       : ':' ;

// Literals
INTEGER_LITERAL : [0-9]+ ;
REAL_LITERAL    : [0-9]+ '.' [0-9]* ([eE] [+-]? [0-9]+)?
                | '.' [0-9]+ ([eE] [+-]? [0-9]+)?
                ;
STRING_LITERAL  : '"' (~["\r\n\\] | '\\' .)* '"'
                | '\'' (~['\r\n\\] | '\\' .)* '\''
                ;

// Identifiers
ID              : [a-zA-Z_][a-zA-Z_0-9]* ;
UNRESTRICTED_NAME : '`' (~[`])* '`' ;

// Special comment for doc
BLOCK_COMMENT_TEXT : '/*' .*? '*/' ;

// Hidden channel
LINE_COMMENT    : '//' ~[\r\n]* -> channel(HIDDEN) ;
WS              : [ \t\r\n]+ -> skip ;
