import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

// Token class with line number
class Token {
    String type, value;
    int line;

    public Token(String type, String value, int line) {
        this.type = type;
        this.value = value;
        this.line = line;
    }

    @Override
    public String toString() {
        return type + "\t" + value + "\tLine: " + line;
    }
}

// SymbolTableEntry class
class SymbolTableEntry {
    String identifier, kind, type, value;
    int size, lineOfDeclaration, lineOfUsage;
    String dimension, address;

    public SymbolTableEntry(String identifier, String kind, String type) {
        this.identifier = identifier;
        this.kind = kind; // "variable" or "function"
        this.type = type;
        this.value = null;
        this.size = "int".equals(type) || "float".equals(type) ? 4
                : "double".equals(type) ? 8 : "char".equals(type) || "bool".equals(type) ? 1 : 0;
        this.dimension = "1D";
        this.lineOfDeclaration = -1;
        this.lineOfUsage = -1;
        this.address = "0x" + Integer.toHexString(System.identityHashCode(this));
    }

    @Override
    public String toString() {
        return identifier + "\t" + kind + "\t" + type + "\t" + (value != null ? value : "N/A") + "\t" + size + "\t" +
                dimension + "\t" + lineOfDeclaration + "\t" + lineOfUsage + "\t" + address;
    }
}

class SyntaxAnalyzer {
    private java.util.List<Token> tokens;
    private int currentIndex;
    private java.util.List<String> errors;
    private Stack<Set<String>> scopeStack;
    private Set<String> globalVariables;
    private Map<String, String> functionReturnTypes;

    public SyntaxAnalyzer(java.util.List<Token> tokens) {
        this.tokens = tokens;
        this.currentIndex = 0;
        this.errors = new ArrayList<>();
        this.scopeStack = new Stack<>();
        this.globalVariables = new HashSet<>();
        this.functionReturnTypes = new HashMap<>();
        scopeStack.push(globalVariables); // Global scope
    }

    public java.util.List<String> analyze() {
        while (currentIndex < tokens.size()) {
            Token token = tokens.get(currentIndex);
            if (isFunctionDeclaration()) {
                analyzeFunctionDeclaration();
            } else if (isDataType(token.value)) {
                analyzeVariableDeclaration();
            } else if (isIdentifier(token)) {
                if (lookAhead().type.equals("Operator") && lookAhead().value.equals("=")) {
                    analyzeAssignment();
                } else if (lookAhead().type.equals("Separator") && lookAhead().value.equals("(")) {
                    analyzeFunctionCall();
                } else {
                    errors.add("Line " + token.line + ": Invalid statement - unexpected identifier '" + token.value + "'");
                    currentIndex++;
                }
            } else if (token.value.equals("if")) {
                analyzeIfStatement();
            } else if (token.value.equals("for")) {
                analyzeForLoop();
            } else if (token.value.equals("while")) {
                analyzeWhileLoop();
            } else if (token.value.equals("return")) {
                analyzeReturnStatement();
            } else if (token.type.equals("Separator") && token.value.equals("{")) {
                scopeStack.push(new HashSet<>()); // New scope
                currentIndex++;
            } else if (token.type.equals("Separator") && token.value.equals("}")) {
                if (scopeStack.size() > 1) { // Preserve global scope
                    scopeStack.pop();
                }
                currentIndex++;
            } else if (!token.type.equals("Separator") && !token.type.equals("Operator")) {
                errors.add("Line " + token.line + ": Unexpected token '" + token.value + "'");
                currentIndex++;
            } else {
                currentIndex++;
            }
        }
        return errors;
    }

    private boolean isFunctionDeclaration() {
        int tempIndex = currentIndex;
        if (tempIndex < tokens.size() && isDataType(tokens.get(tempIndex).value)) {
            tempIndex++;
            if (tempIndex < tokens.size() && isIdentifier(tokens.get(tempIndex))) {
                tempIndex++;
                if (tempIndex < tokens.size() && tokens.get(tempIndex).value.equals("(")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void analyzeFunctionDeclaration() {
        int line = tokens.get(currentIndex).line;
        String returnType = tokens.get(currentIndex).value;
        currentIndex++; // consume data type
        
        if (currentIndex < tokens.size() && isIdentifier(tokens.get(currentIndex))) {
            String functionName = tokens.get(currentIndex).value;
            functionReturnTypes.put(functionName, returnType);
            currentIndex++; // consume identifier
            
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("(")) {
                currentIndex++; // consume '('
                
                // Analyze parameters
                analyzeParameters(functionName);
                
                // Check for closing parenthesis
                if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(")")) {
                    errors.add("Line " + line + ": Missing closing parenthesis in function declaration");
                } else {
                    currentIndex++; // consume ')'
                }
                
                // Check for function body or semicolon (for declarations)
                if (currentIndex < tokens.size()) {
                    if (tokens.get(currentIndex).value.equals("{")) {
                        // Function definition with body
                        scopeStack.push(new HashSet<>());
                        skipBlock();
                    } else if (tokens.get(currentIndex).value.equals(";")) {
                        // Function declaration without body
                        currentIndex++;
                    } else {
                        errors.add("Line " + line + ": Expected '{' or ';' after function declaration");
                    }
                }
            } else {
                errors.add("Line " + line + ": Expected '(' after function name");
            }
        } else {
            errors.add("Line " + line + ": Expected identifier after return type");
        }
    }

    private void analyzeParameters(String functionName) {
        int line = tokens.get(currentIndex).line;
        boolean expectParam = true;
        
        while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(")")) {
            Token token = tokens.get(currentIndex);
            
            if (token.value.equals(",")) {
                if (expectParam) {
                    errors.add("Line " + line + ": Unexpected comma in parameter list");
                }
                expectParam = true;
                currentIndex++;
                continue;
            }
            
            if (expectParam) {
                if (isDataType(token.value)) {
                    String paramType = token.value;
                    currentIndex++;
                    
                    if (currentIndex < tokens.size() && isIdentifier(tokens.get(currentIndex))) {
                        String paramName = tokens.get(currentIndex).value;
                        addVariableToScope(paramName);
                        currentIndex++;
                        expectParam = false;
                        
                        // Check for array parameter
                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                            currentIndex++;
                            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                                currentIndex++;
                            } else {
                                errors.add("Line " + line + ": Expected ']' in array parameter declaration");
                            }
                        }
                    } else {
                        errors.add("Line " + line + ": Expected parameter name after type");
                    }
                } else {
                    errors.add("Line " + line + ": Expected parameter type in function declaration");
                    currentIndex++;
                }
            } else {
                errors.add("Line " + line + ": Expected comma between parameters");
                currentIndex++;
            }
        }
    }

    private boolean isDataType(String value) {
        return value.equals("int") || value.equals("float") || value.equals("double") ||
                value.equals("char") || value.equals("bool") || value.equals("string") ||
                value.equals("void");
    }

    private boolean isIdentifier(Token token) {
        return token.type.equals("Identifier");
    }

    private Token lookAhead() {
        if (currentIndex + 1 < tokens.size()) {
            return tokens.get(currentIndex + 1);
        }
        return new Token("EOF", "", -1);
    }

    private void analyzeVariableDeclaration() {
        Token dataTypeToken = tokens.get(currentIndex);
        int line = dataTypeToken.line;
        String dataType = dataTypeToken.value;
        currentIndex++;

        if (currentIndex < tokens.size() && isIdentifier(tokens.get(currentIndex))) {
            String varName = tokens.get(currentIndex).value;
            if (isVariableDeclared(varName)) {
                errors.add("Line " + line + ": Variable '" + varName + "' already declared in this scope");
            } else {
                addVariableToScope(varName);
            }
            currentIndex++;

            boolean isArray = false;
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                isArray = true;
                currentIndex++;
                if (currentIndex < tokens.size() && (tokens.get(currentIndex).type.startsWith("Literal")
                        || isIdentifier(tokens.get(currentIndex)))) {
                    currentIndex++;
                } else {
                    errors.add("Line " + line + ": Expected array size after '['");
                }
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                    currentIndex++;
                } else {
                    errors.add("Line " + line + ": Expected ']' after array size");
                }
            }

            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Operator")
                    && tokens.get(currentIndex).value.equals("=")) {
                currentIndex++;
                if (!isArray) {
                    if (currentIndex < tokens.size() && tokens.get(currentIndex).type.startsWith("Literal")) {
                        Token literalToken = tokens.get(currentIndex);
                        if (!isTypeCompatible(dataType, literalToken)) {
                            errors.add("Line " + line + ": Type mismatch: cannot assign " + literalToken.type + " to "
                                    + dataType);
                        }
                        if (dataType.equals("char") && literalToken.type.equals("Literal (Char)")
                                && (literalToken.value.length() != 3 || !literalToken.value.startsWith("'")
                                        || !literalToken.value.endsWith("'"))) {
                            errors.add("Line " + line + ": Invalid char literal (must be a single character)");
                        }
                        currentIndex++;
                    } else if (currentIndex < tokens.size() && isIdentifier(tokens.get(currentIndex))) {
                        if (!isVariableDeclared(tokens.get(currentIndex).value)) {
                            errors.add("Line " + line + ": Variable '" + tokens.get(currentIndex).value
                                    + "' used before declaration");
                        }
                        currentIndex++;
                    } else {
                        errors.add("Line " + line + ": Expected value after '='");
                    }
                } else {
                    errors.add("Line " + line + ": Array initialization not supported in this context");
                    while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                        currentIndex++;
                    }
                }
            }

            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals(";")) {
                currentIndex++;
            } else {
                errors.add("Line " + line + ": Missing semicolon after variable declaration");
            }
        } else {
            errors.add("Line " + line + ": Expected identifier after data type");
        }
    }

    private boolean isTypeCompatible(String dataType, Token valueToken) {
        String valueType = valueToken.type;
        if (dataType.equals("int") && valueType.equals("Literal (Int)"))
            return true;
        if (dataType.equals("float") && (valueType.equals("Literal (Float)") || valueType.equals("Literal (Int)")))
            return true;
        if (dataType.equals("double") && (valueType.equals("Literal (Float)") || valueType.equals("Literal (Int)")))
            return true;
        if (dataType.equals("char") && valueType.equals("Literal (Char)"))
            return true;
        if (dataType.equals("string") && valueType.equals("Literal (String)"))
            return true;
        if (dataType.equals("bool") && (valueType.equals("Keyword")
                && (valueToken.value.equals("true") || valueToken.value.equals("false"))))
            return true;
        return false;
    }

    private void analyzeAssignment() {
        Token token = tokens.get(currentIndex);
        int line = token.line;
        if (isIdentifier(token)) {
            String varName = token.value;
            if (!isVariableDeclared(varName)) {
                errors.add("Line " + line + ": Variable '" + varName + "' used before declaration");
            }
            currentIndex++;
            boolean isArray = false;
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                isArray = true;
                currentIndex++;
                if (currentIndex < tokens.size() && (isIdentifier(tokens.get(currentIndex))
                        || tokens.get(currentIndex).type.startsWith("Literal"))) {
                    currentIndex++;
                    if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                        currentIndex++;
                    } else {
                        errors.add("Line " + line + ": Expected ']' after array index");
                    }
                } else {
                    errors.add("Line " + line + ": Expected array index after '['");
                }
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Operator")
                    && tokens.get(currentIndex).value.equals("=")) {
                currentIndex++;
                int startIndex = currentIndex;
                while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                    Token current = tokens.get(currentIndex);
                    if (isIdentifier(current) && !isVariableDeclared(current.value)) {
                        errors.add("Line " + line + ": Variable '" + current.value + "' used before declaration");
                    }
                    currentIndex++;
                }
                if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(";")) {
                    errors.add("Line " + line + ": Missing semicolon after assignment");
                } else {
                    currentIndex++;
                    if (startIndex == currentIndex - 1) {
                        if (isArray) {
                            errors.add("Line " + line + ": Missing index in array assignment");
                        } else {
                            errors.add("Line " + line + ": Expected value after '='");
                        }
                    } else {
                        Token lastToken = tokens.get(currentIndex - 2);
                        if (lastToken.type.equals("Operator")) {
                            errors.add("Line " + line + ": Incomplete expression in assignment");
                        }
                    }
                }
            } else {
                errors.add("Line " + line + ": Expected '=' in assignment");
            }
        } else {
            errors.add("Line " + line + ": Expected identifier in assignment");
            currentIndex++;
        }
    }

    private void analyzeIfStatement() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                && tokens.get(currentIndex).value.equals("(")) {
            currentIndex++;
            int openParens = 1;
            int conditionStart = currentIndex;
            boolean hasComparison = false;
            while (currentIndex < tokens.size() && openParens > 0) {
                Token token = tokens.get(currentIndex);
                if (token.type.equals("Separator")) {
                    if (token.value.equals("("))
                        openParens++;
                    else if (token.value.equals(")"))
                        openParens--;
                } else if (token.type.equals("Operator") && isComparisonOperator(token.value)) {
                    hasComparison = true;
                } else if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                }
                currentIndex++;
            }
            if (openParens > 0) {
                errors.add("Line " + line + ": Missing closing parenthesis in if statement");
            } else if (conditionStart == currentIndex - 1) {
                errors.add("Line " + line + ": Empty condition in if statement");
            } else if (!hasComparison) {
                errors.add("Line " + line + ": No comparison operator in if condition");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals("{")) {
                scopeStack.push(new HashSet<>());
                skipBlock();
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("else")) {
                    currentIndex++;
                    if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("if")) {
                        analyzeIfStatement(); // Recursive call for else-if
                    } else if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                            && tokens.get(currentIndex).value.equals("{")) {
                        scopeStack.push(new HashSet<>());
                        skipBlock();
                    } else if (currentIndex < tokens.size()) {
                        // Allow single statement after else
                        while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                            Token token = tokens.get(currentIndex);
                            if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                                errors.add("Line " + token.line + ": Variable '" + token.value
                                        + "' used before declaration");
                            }
                            currentIndex++;
                        }
                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                            currentIndex++;
                        } else {
                            errors.add("Line " + line + ": Missing semicolon after else statement");
                        }
                    } else {
                        errors.add("Line " + line + ": Expected statement or '{' after 'else'");
                    }
                }
            } else {
                errors.add("Line " + line + ": Expected '{' after if condition");
            }
        } else {
            errors.add("Line " + line + ": Missing opening parenthesis in if statement");
        }
    }

    private void analyzeForLoop() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                && tokens.get(currentIndex).value.equals("(")) {
            currentIndex++;
            scopeStack.push(new HashSet<>());
            // Initialization
            if (currentIndex < tokens.size() && isDataType(tokens.get(currentIndex).value)) {
                analyzeVariableDeclaration();
            } else {
                while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                    currentIndex++;
                }
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                    currentIndex++;
                } else {
                    errors.add("Line " + line + ": Missing semicolon in for loop initialization");
                }
            }
            // Condition
            int conditionStart = currentIndex;
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                Token token = tokens.get(currentIndex);
                if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                }
                currentIndex++;
            }
            if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(";")) {
                errors.add("Line " + line + ": Missing semicolon in for loop condition");
            } else {
                currentIndex++;
            }
            // Increment
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(")")) {
                Token token = tokens.get(currentIndex);
                if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                }
                currentIndex++;
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(")")) {
                currentIndex++;
                if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                        && tokens.get(currentIndex).value.equals("{")) {
                    skipBlock();
                } else {
                    errors.add("Line " + line + ": Expected '{' after for loop");
                }
            } else {
                errors.add("Line " + line + ": Missing closing parenthesis in for loop");
            }
            scopeStack.pop();
        } else {
            errors.add("Line " + line + ": Missing opening parenthesis in for loop");
        }
    }

    private void analyzeWhileLoop() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator") &&
                tokens.get(currentIndex).value.equals("(")) {
            currentIndex++;
            int openParens = 1;
            boolean hasComparison = false;
            while (currentIndex < tokens.size() && openParens > 0) {
                Token token = tokens.get(currentIndex);
                if (token.type.equals("Separator")) {
                    if (token.value.equals("("))
                        openParens++;
                    else if (token.value.equals(")"))
                        openParens--;
                } else if (token.type.equals("Operator") && isComparisonOperator(token.value)) {
                    hasComparison = true;
                } else if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                }
                currentIndex++;
            }
            if (openParens > 0) {
                errors.add("Line " + line + ": Missing closing parenthesis in while loop");
            } else if (!hasComparison) {
                errors.add("Line " + line + ": No comparison operator in while condition");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator") &&
                    tokens.get(currentIndex).value.equals("{")) {
                scopeStack.push(new HashSet<>());
                skipBlock();
            } else {
                errors.add("Line " + line + ": Expected '{' after while loop");
            }
        } else {
            errors.add("Line " + line + ": Missing opening parenthesis in while loop");
        }
    }

    private void analyzeFunctionCall() {
        Token funcToken = tokens.get(currentIndex);
        int line = funcToken.line;
        String funcName = funcToken.value;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                && tokens.get(currentIndex).value.equals("(")) {
            currentIndex++;
            boolean expectArg = true;
            int argCount = 0;
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(")")) {
                Token token = tokens.get(currentIndex);
                if (token.type.equals("Separator") && token.value.equals(",")) {
                    if (expectArg) {
                        errors.add("Line " + line + ": Missing argument before comma in function call");
                    }
                    expectArg = true;
                } else if (isIdentifier(token) || token.type.startsWith("Literal")) {
                    if (!expectArg) {
                        errors.add("Line " + line + ": Expected ',' between arguments");
                    }
                    if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                        errors.add("Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                    }
                    expectArg = false;
                    argCount++;
                } else {
                    errors.add("Line " + line + ": Invalid token '" + token.value + "' in function call");
                }
                currentIndex++;
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(")")) {
                currentIndex++;
                if (expectArg && argCount == 0) {
                    // Allow empty argument list
                } else if (expectArg) {
                    errors.add("Line " + line + ": Missing argument after comma in function call");
                }
            } else {
                errors.add("Line " + line + ": Missing closing parenthesis in function call");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals(";")) {
                currentIndex++;
            } else {
                errors.add("Line " + line + ": Missing semicolon after function call");
            }
        } else {
            errors.add("Line " + line + ": Expected '(' after function name");
        }
    }

    private void analyzeReturnStatement() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        
        // Check if we're in a function
        boolean inFunction = scopeStack.size() > 1;
        String expectedReturnType = "void";
        
        if (inFunction) {
            // Try to find the function name in the call stack (simplified)
            for (int i = tokens.size() - 1; i >= 0; i--) {
                Token t = tokens.get(i);
                if (t.type.equals("Identifier") && functionReturnTypes.containsKey(t.value)) {
                    expectedReturnType = functionReturnTypes.get(t.value);
                    break;
                }
            }
        }
        
        if (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
            // There's a return value
            if (expectedReturnType.equals("void")) {
                errors.add("Line " + line + ": Void function should not return a value");
            }
            
            // Check type compatibility if we can
            Token returnValue = tokens.get(currentIndex);
            if (returnValue.type.startsWith("Literal")) {
                if (!isTypeCompatible(expectedReturnType, returnValue)) {
                    errors.add("Line " + line + ": Return type mismatch: cannot return " + 
                             returnValue.type + " from function expecting " + expectedReturnType);
                }
            }
            
            // Skip the return value
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                currentIndex++;
            }
        } else if (!expectedReturnType.equals("void")) {
            errors.add("Line " + line + ": Non-void function should return a value");
        }
        
        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
            currentIndex++;
        } else {
            errors.add("Line " + line + ": Missing semicolon after return statement");
        }
    }

    private void skipBlock() {
        currentIndex++;
        int braceCount = 1;
        while (currentIndex < tokens.size() && braceCount > 0) {
            Token token = tokens.get(currentIndex);
            if (token.type.equals("Separator")) {
                if (token.value.equals("{")) {
                    braceCount++;
                    scopeStack.push(new HashSet<>());
                } else if (token.value.equals("}")) {
                    braceCount--;
                    if (braceCount > 0)
                        scopeStack.pop();
                }
            }
            currentIndex++;
        }
        if (braceCount > 0) {
            errors.add("Line " + tokens.get(currentIndex - 1).line + ": Missing closing brace '}'");
        }
    }

    private boolean isVariableDeclared(String varName) {
        for (Set<String> scope : scopeStack) {
            if (scope.contains(varName))
                return true;
        }
        return false;
    }

    private void addVariableToScope(String varName) {
        scopeStack.peek().add(varName);
    }

    private boolean isComparisonOperator(String op) {
        return op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") ||
                op.equals("<=") || op.equals(">=");
    }
}

public class WppScannerGUI extends JFrame {
    private JTextPane codeArea;
    private JTextArea lineNumbers;
    private JTable tokensTable, symbolTable, errorsTable;
    private DefaultTableModel tokensTableModel, symbolTableModel, errorsTableModel;
    private JLabel statusLabelLeft, statusLabelCenter, statusLabelRight;
    private JMenuItem toggleDarkModeItem;
    private UndoManager undoManager;
    private boolean darkMode = false;
    private static final int MAX_RECENTS = 5;
    private final LinkedList<File> recentFiles = new LinkedList<>();
    private JMenu openRecentMenu;
    private Map<String, SymbolTableEntry> symbolTableMap;
    private java.util.List<Token> tokens;
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "int", "float", "double", "char", "string", "bool", "void", "class", "namespace",
            "public", "private", "protected", "static", "virtual", "const", "constexpr",
            "if", "else", "switch", "case", "for", "while", "do", "return", "break", "continue",
            "new", "delete", "sizeof", "typedef", "using", "struct", "union", "enum", "nullptr", "true", "false",
            "cout"));
    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
            "=", "+", "-", "*", "/", "%", "++", "--", "==", "!=", "<", "<=", ">", ">=", "&&", "||", "!",
            "&", "|", "^", "~", "<<", ">>", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=",
            "->", "::", "?", ":"));
    private static final Set<String> SEPARATORS = new HashSet<>(Arrays.asList("(", ")", "{", "}", "[", "]", ";", ","));

    private Style defaultStyle, keywordStyle, errorStyle;

    public WppScannerGUI() {
        super("Wpp Compiler by Binary Brains");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("Table.gridColor", new Color(230, 230, 230));
            UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
            UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
            UIManager.put("TabbedPane.tabsOverlapBorder", true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Menu bar setup
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(createMenuItem("New", "/icons/new.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), e -> {
                    codeArea.setText("");
                    statusLabelLeft.setText("New file");
                    updateLineNumbers();
                    updateDocumentStats();
                }));
        fileMenu.add(createMenuItem("Open", "/icons/open.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), e -> openFile()));
        openRecentMenu = new JMenu("Open Recent");
        openRecentMenu.setIcon(new ImageIcon(getClass().getResource("/icons/history.png")));
        fileMenu.add(openRecentMenu);
        fileMenu.add(createMenuItem("Save", "/icons/save.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), e -> saveFile()));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("Exit", "/icons/exit.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), e -> System.exit(0)));

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(createMenuItem("Undo", "/icons/undo.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), e -> {
                    if (undoManager.canUndo())
                        undoManager.undo();
                }));
        editMenu.add(createMenuItem("Redo", "/icons/redo.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), e -> {
                    if (undoManager.canRedo())
                        undoManager.redo();
                }));

        JMenu compileMenu = new JMenu("Compile");
        compileMenu.add(createMenuItem("Run Scanner", "/icons/run.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), e -> runScanner()));

        JMenu searchMenu = new JMenu("Search");
        searchMenu.add(createMenuItem("Find/Replace...", "/icons/find.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                e -> new FindReplaceDialog(this, codeArea).setVisible(true)));

        JMenu viewMenu = new JMenu("View");
        toggleDarkModeItem = createMenuItem("Toggle Dark Mode", "/icons/dark_mode.png",
                KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), e -> toggleDarkMode());
        viewMenu.add(toggleDarkModeItem);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(createMenuItem("About", "/icons/about.png", null, e -> showAboutDialog()));
        helpMenu.add(createMenuItem("Keyboard Shortcuts", "/icons/keyboard.png", null, e -> showShortcutsHelp()));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(compileMenu);
        menuBar.add(searchMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // Toolbar setup
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        toolBar.setBackground(new Color(245, 245, 245));
        toolBar.add(createToolbarButton("Open", "/icons/open.png", "Open", e -> openFile()));
        toolBar.addSeparator();
        toolBar.add(createToolbarButton("Save", "/icons/save.png", "Save", e -> saveFile()));
        toolBar.addSeparator();
        toolBar.add(createToolbarButton("Run", "/icons/run.png", "Run Scanner", e -> runScanner()));

        // Code area setup
        codeArea = new JTextPane();
        initializeStyles();
        String fontName = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .contains("JetBrains Mono") ? "JetBrains Mono" : Font.MONOSPACED;
        codeArea.setFont(new Font(fontName, Font.PLAIN, 14));
        codeArea.setDocument(new DefaultStyledDocument());
        codeArea.putClientProperty("caretWidth", 2);
        codeArea.getDocument().addDocumentListener(new SyntaxHighlightListener());
        codeArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        undoManager = new UndoManager();
        codeArea.getDocument().addUndoableEditListener(undoManager);

        // Line numbers
        lineNumbers = new JTextArea("1");
        lineNumbers.setBackground(Color.LIGHT_GRAY);
        lineNumbers.setEditable(false);
        lineNumbers.setFont(new Font(fontName, Font.PLAIN, 14));
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        lineNumbers.setMargin(new Insets(0, 5, 0, 5));
        codeArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateLineNumbers();
                updateDocumentStats();
            }

            public void removeUpdate(DocumentEvent e) {
                updateLineNumbers();
                updateDocumentStats();
            }

            public void changedUpdate(DocumentEvent e) {
                updateLineNumbers();
                updateDocumentStats();
            }
        });

        JScrollPane codeScrollPane = new JScrollPane(codeArea);
        codeScrollPane.setRowHeaderView(lineNumbers);

        // Tables setup
        tokensTableModel = new DefaultTableModel(new Object[] { "Token Type", "Value", "Line" }, 0);
        tokensTable = new JTable(tokensTableModel);
        tokensTable.setRowHeight(25);
        tokensTable.setIntercellSpacing(new Dimension(10, 0));
        tokensTable.setShowGrid(true);
        tokensTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tokensTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tokensTable.setFillsViewportHeight(true);

        symbolTableModel = new DefaultTableModel(new Object[] { "Name", "Kind", "Type", "Value", "Size", "Dimension",
                "Line of Declaration", "Line of Usage", "Address" }, 0);
        symbolTable = new JTable(symbolTableModel);
        symbolTable.setRowHeight(25);
        symbolTable.setIntercellSpacing(new Dimension(10, 0));
        symbolTable.setShowGrid(true);
        symbolTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        symbolTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        symbolTable.setFillsViewportHeight(true);

        errorsTableModel = new DefaultTableModel(new Object[] { "Line", "Error Message" }, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Integer.class : String.class;
            }
        };
        errorsTable = new JTable(errorsTableModel);
        errorsTable.setRowHeight(25);
        errorsTable.setIntercellSpacing(new Dimension(10, 0));
        errorsTable.setShowGrid(true);
        errorsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        errorsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        errorsTable.setFillsViewportHeight(true);
        
        // Set error text color to red
       errorsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
            boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        c.setForeground(Color.RED);
        return c;
    }
});

        errorsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = errorsTable.getSelectedRow();
                    if (row != -1) {
                        Object lineObj = errorsTableModel.getValueAt(row, 0);
                        if (lineObj instanceof Integer) {
                            int line = (Integer) lineObj;
                            try {
                                int offset = codeArea.getDocument().getDefaultRootElement().getElement(line - 1)
                                        .getStartOffset();
                                codeArea.setCaretPosition(offset);
                                codeArea.requestFocus();
                            } catch (Exception ex) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        });

        JTabbedPane tablesTabbedPane = new JTabbedPane();
        tablesTabbedPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tablesTabbedPane.addTab("Tokens", new JScrollPane(tokensTable));
        tablesTabbedPane.addTab("Symbol Table", new JScrollPane(symbolTable));
        tablesTabbedPane.addTab("Syntax Errors", new JScrollPane(errorsTable));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeScrollPane, tablesTabbedPane);
        mainSplit.setDividerLocation(400);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setDividerSize(8);

        // Status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
        statusLabelLeft = new JLabel("Ready");
        statusLabelCenter = new JLabel("Words: 0 | Chars: 0 | Lines: 0");
        statusLabelCenter.setHorizontalAlignment(JLabel.CENTER);
        statusLabelRight = new JLabel("Dark Mode OFF");
        statusPanel.add(statusLabelLeft, BorderLayout.WEST);
        statusPanel.add(statusLabelCenter, BorderLayout.CENTER);
        statusPanel.add(statusLabelRight, BorderLayout.EAST);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(toolBar, BorderLayout.NORTH);
        mainPanel.add(mainSplit, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        getContentPane().add(mainPanel);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon(getClass().getResource("/icons/h3_logo.png")).getImage());
        setVisible(true);
        updateLineNumbers();
        updateRecentMenu();
    }

    private JMenuItem createMenuItem(String text, String iconPath, KeyStroke accelerator, ActionListener listener) {
        JMenuItem item = new JMenuItem(text, iconPath != null ? new ImageIcon(getClass().getResource(iconPath)) : null);
        if (accelerator != null)
            item.setAccelerator(accelerator);
        if (listener != null)
            item.addActionListener(listener);
        return item;
    }

    private JButton createToolbarButton(String text, String iconPath, String tooltip, ActionListener listener) {
        JButton button = new JButton(text, new ImageIcon(getClass().getResource(iconPath)));
        button.setToolTipText(tooltip);
        if (listener != null)
            button.addActionListener(listener);
        return button;
    }

    private void initializeStyles() {
        StyledDocument doc = codeArea.getStyledDocument();
        Style base = doc.getStyle(StyleContext.DEFAULT_STYLE);
        defaultStyle = doc.addStyle("default", base);
        StyleConstants.setForeground(defaultStyle, Color.BLACK);
        keywordStyle = doc.addStyle("keyword", base);
        StyleConstants.setForeground(keywordStyle, new Color(0, 119, 170));
        StyleConstants.setBold(keywordStyle, true);
        errorStyle = doc.addStyle("error", base);
        StyleConstants.setForeground(errorStyle, Color.RED);
        StyleConstants.setBold(errorStyle, true);
    }

    private void applySyntaxHighlighting() {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = codeArea.getStyledDocument();
                String text = doc.getText(0, doc.getLength());
                doc.setCharacterAttributes(0, doc.getLength(), defaultStyle, true);
                int pos = 0;
                for (String token : tokenize(text)) {
                    if (!token.isEmpty() && KEYWORDS.contains(token)) {
                        int start = text.indexOf(token, pos);
                        if (start >= 0) {
                            doc.setCharacterAttributes(start, token.length(), keywordStyle, true);
                            pos = start + token.length();
                        }
                    }
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private class SyntaxHighlightListener implements DocumentListener {
        public void insertUpdate(DocumentEvent e) {
            applySyntaxHighlighting();
        }

        public void removeUpdate(DocumentEvent e) {
            applySyntaxHighlighting();
        }

        public void changedUpdate(DocumentEvent e) {
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "<html><b>Wpp Compiler by Bianry Brains</b><br><br>" +
                "<table border='0' cellpadding='3'>" +
                "<tr><td><font color='#0066cc'>Version:</font></td><td>1.2</td></tr>" +
                "<tr><td><font color='#0066cc'>Features:</font></td><td>Syntax Analysis, Symbol Table, Error Checking</td></tr>" +
                "<tr><td><font color='#0066cc'>Created by:</font></td><td>Arfan,Moiz,Zain</td></tr>" +
                "</table></html>",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showShortcutsHelp() {
        JOptionPane.showMessageDialog(this,
                "<html><b>Keyboard Shortcuts</b><br><br>" +
                "<table border='0' cellpadding='3'>" +
                "<tr><td><font color='#0066cc'>New File</font></td><td>Ctrl+N</td></tr>" +
                "<tr><td><font color='#0066cc'>Open File</font></td><td>Ctrl+O</td></tr>" +
                "<tr><td><font color='#0066cc'>Save File</font></td><td>Ctrl+S</td></tr>" +
                "<tr><td><font color='#0066cc'>Exit</font></td><td>Ctrl+Q</td></tr>" +
                "<tr><td><font color='#0066cc'>Undo</font></td><td>Ctrl+Z</td></tr>" +
                "<tr><td><font color='#0066cc'>Redo</font></td><td>Ctrl+Y</td></tr>" +
                "<tr><td><font color='#0066cc'>Run Scanner</font></td><td>Ctrl+R</td></tr>" +
                "<tr><td><font color='#0066cc'>Find/Replace</font></td><td>Ctrl+F</td></tr>" +
                "<tr><td><font color='#0066cc'>Toggle Dark Mode</font></td><td>Ctrl+D</td></tr>" +
                "</table></html>",
                "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateLineNumbers() {
        String[] lines = codeArea.getText().split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines.length; i++)
            sb.append(i).append("\n");
        lineNumbers.setText(sb.toString());
    }

    private void updateDocumentStats() {
        String text = codeArea.getText();
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        statusLabelCenter.setText(String.format("Words: %d | Chars: %d | Lines: %d", words, chars, lines));
    }

    private void toggleDarkMode() {
        darkMode = !darkMode;
        Color bg = darkMode ? new Color(43, 43, 43) : new Color(250, 250, 250);
        Color fg = darkMode ? new Color(214, 214, 214) : new Color(33, 33, 33);
        Color headerBg = darkMode ? new Color(60, 63, 65) : new Color(240, 240, 240);
        Color gridColor = darkMode ? new Color(80, 80, 80) : new Color(230, 230, 230);
        
        codeArea.setBackground(bg);
        codeArea.setForeground(fg);
        codeArea.setCaretColor(darkMode ? Color.WHITE : Color.BLACK);
        StyleConstants.setForeground(defaultStyle, fg);
        StyleConstants.setForeground(keywordStyle, darkMode ? new Color(204, 120, 50) : new Color(0, 119, 170));
        lineNumbers.setBackground(darkMode ? new Color(60, 63, 65) : new Color(240, 240, 240));
        lineNumbers.setForeground(fg);
        
        tokensTable.setBackground(bg);
        tokensTable.setForeground(fg);
        tokensTable.setGridColor(gridColor);
        tokensTable.getTableHeader().setBackground(headerBg);
        tokensTable.getTableHeader().setForeground(fg);
        
        symbolTable.setBackground(bg);
        symbolTable.setForeground(fg);
        symbolTable.setGridColor(gridColor);
        symbolTable.getTableHeader().setBackground(headerBg);
        symbolTable.getTableHeader().setForeground(fg);
        
        errorsTable.setBackground(bg);
        errorsTable.setForeground(Color.RED); // Keep errors red in both modes
        errorsTable.setGridColor(gridColor);
        errorsTable.getTableHeader().setBackground(headerBg);
        errorsTable.getTableHeader().setForeground(fg);
        
        statusLabelRight.setText("Dark Mode " + (darkMode ? "ON" : "OFF"));
        applySyntaxHighlighting();
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            openFileDirect(chooser.getSelectedFile());
    }

    private void openFileDirect(File file) {
        if (file == null)
            return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                content.append(line).append("\n");
            codeArea.setText(content.toString());
            applySyntaxHighlighting();
            statusLabelLeft.setText("Opened: " + file.getName());
            updateLineNumbers();
            updateDocumentStats();
            addToRecent(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error opening file: " + ex.getMessage());
            statusLabelLeft.setText("Error opening file");
        }
    }

    private void saveFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.write(codeArea.getText());
                statusLabelLeft.setText("Saved: " + file.getName());
                addToRecent(file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
                statusLabelLeft.setText("Error saving file");
            }
        }
    }

    private void addToRecent(File file) {
        recentFiles.remove(file);
        recentFiles.addFirst(file);
        while (recentFiles.size() > MAX_RECENTS)
            recentFiles.removeLast();
        updateRecentMenu();
    }

    private void updateRecentMenu() {
        openRecentMenu.removeAll();
        openRecentMenu.setEnabled(!recentFiles.isEmpty());
        for (File f : recentFiles) {
            JMenuItem item = new JMenuItem(f.getName());
            item.setToolTipText(f.getAbsolutePath());
            item.addActionListener(e -> openFileDirect(f));
            openRecentMenu.add(item);
        }
    }

    private void runScanner() {
        tokensTableModel.setRowCount(0);
        symbolTableModel.setRowCount(0);
        errorsTableModel.setRowCount(0);
        symbolTableMap = new LinkedHashMap<>();
        tokens = new ArrayList<>();
        String text = codeArea.getText();
        if (text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No code to scan!");
            statusLabelLeft.setText("No code to scan!");
            return;
        }
        String[] lines = text.split("\\r?\\n");
        boolean inBlockComment = false;
        String currentType = null;
        int lineNum = 1;
        for (String line : lines) {
            String processedLine = "";
            int index = 0;
            while (index < line.length()) {
                if (!inBlockComment) {
                    int inlineIndex = line.indexOf("//", index), blockIndex = line.indexOf("/*", index);
                    if (inlineIndex == -1 && blockIndex == -1) {
                        processedLine += line.substring(index);
                        break;
                    }
                    if (blockIndex == -1 || (inlineIndex != -1 && inlineIndex < blockIndex)) {
                        processedLine += line.substring(index, inlineIndex);
                        break;
                    }
                    processedLine += line.substring(index, blockIndex);
                    index = blockIndex + 2;
                    inBlockComment = true;
                } else {
                    int endBlock = line.indexOf("*/", index);
                    index = endBlock == -1 ? line.length() : endBlock + 2;
                    if (endBlock != -1)
                        inBlockComment = false;
                }
            }
            processedLine = processedLine.trim();
            if (!processedLine.isEmpty()) {
                for (String part : tokenize(processedLine)) {
                    if (part.isEmpty())
                        continue;
                    if (KEYWORDS.contains(part)) {
                        tokens.add(new Token("Keyword", part, lineNum));
                        currentType = part;
                    } else if (OPERATORS.contains(part))
                        tokens.add(new Token("Operator", part, lineNum));
                    else if (SEPARATORS.contains(part))
                        tokens.add(new Token("Separator", part, lineNum));
                    else if (part.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        tokens.add(new Token("Identifier", part, lineNum));
                        if (currentType != null)
                            symbolTableMap.putIfAbsent(part,
                                    new SymbolTableEntry(part, currentType, "Not initialized"));
                    } else if (part.matches("\\d+"))
                        tokens.add(new Token("Literal (Int)", part, lineNum));
                    else if (part.matches("\\d+\\.\\d+"))
                        tokens.add(new Token("Literal (Float)", part, lineNum));
                    else if (part.startsWith("\"") && part.endsWith("\""))
                        tokens.add(new Token("Literal (String)", part, lineNum));
                    else if (part.startsWith("'") && part.endsWith("'") && part.length() == 3)
                        tokens.add(new Token("Literal (Char)", part, lineNum));
                    else
                        System.out.println("Unrecognized token: " + part);
                }
            }
            lineNum++;
        }

        SyntaxAnalyzer syntaxAnalyzer = new SyntaxAnalyzer(tokens);
        java.util.List<String> syntaxErrors = syntaxAnalyzer.analyze();
        for (String error : syntaxErrors) {
            lineNum = -1;
            if (error.startsWith("Line ")) {
                try {
                    lineNum = Integer.parseInt(error.substring(5, error.indexOf(":")));
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    // Continue with -1
                }
            }
            errorsTableModel.addRow(new Object[] { lineNum != -1 ? lineNum : "N/A", error });
        }

        updateSymbolTable();
        populateTables();
        statusLabelLeft.setText("Scan complete: " + tokens.size() + " tokens");
    }

    private java.util.List<String> tokenize(String line) {
        java.util.List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inString = false, inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inString) {
                if (ch == '\\' && i + 1 < line.length()) {
                    buffer.append('\\').append(line.charAt(++i));
                } else if (ch == '"') {
                    buffer.append('"');
                    result.add(buffer.toString());
                    buffer.setLength(0);
                    inString = false;
                } else {
                    buffer.append(ch);
                }
            } else if (inChar) {
                if (ch == '\\' && i + 1 < line.length()) {
                    buffer.append('\\').append(line.charAt(++i));
                } else if (ch == '\'') {
                    buffer.append('\'');
                    result.add(buffer.toString());
                    buffer.setLength(0);
                    inChar = false;
                } else {
                    buffer.append(ch);
                }
            } else {
                if (ch == '"') {
                    if (buffer.length() > 0)
                        result.add(buffer.toString());
                    buffer.setLength(0);
                    buffer.append('"');
                    inString = true;
                } else if (ch == '\'') {
                    if (buffer.length() > 0)
                        result.add(buffer.toString());
                    buffer.setLength(0);
                    buffer.append('\'');
                    inChar = true;
                } else if (Character.isWhitespace(ch)) {
                    if (buffer.length() > 0)
                        result.add(buffer.toString());
                    buffer.setLength(0);
                } else if (SEPARATORS.contains(String.valueOf(ch)) || OPERATORS.contains(String.valueOf(ch))) {
                    if (buffer.length() > 0)
                        result.add(buffer.toString());
                    buffer.setLength(0);
                    result.add(String.valueOf(ch));
                } else {
                    buffer.append(ch);
                }
            }
        }
        if (buffer.length() > 0)
            result.add(buffer.toString());
        return result;
    }

    private void updateSymbolTable() {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if ("Identifier".equals(token.type)) {
                String identifier = token.value;
                int lineNum = token.line;
                if (i > 0 && "Keyword".equals(tokens.get(i - 1).type) && KEYWORDS.contains(tokens.get(i - 1).value)) {
                    if (symbolTableMap.containsKey(identifier)) {
                        SymbolTableEntry entry = symbolTableMap.get(identifier);
                        entry.lineOfDeclaration = lineNum;
                        if (i + 1 < tokens.size() && "Separator".equals(tokens.get(i + 1).type)
                                && "[".equals(tokens.get(i + 1).value))
                            entry.dimension = "Array";
                    }
                } else if (symbolTableMap.containsKey(identifier) && symbolTableMap.get(identifier).lineOfUsage == -1) {
                    symbolTableMap.get(identifier).lineOfUsage = lineNum;
                }
                if (i + 2 < tokens.size() && "Operator".equals(tokens.get(i + 1).type)
                        && "=".equals(tokens.get(i + 1).value) && tokens.get(i + 2).type.startsWith("Literal")) {
                    if (symbolTableMap.containsKey(identifier)) {
                        SymbolTableEntry entry = symbolTableMap.get(identifier);
                        entry.value = tokens.get(i + 2).value;
                        if (entry.lineOfUsage == -1)
                            entry.lineOfUsage = lineNum;
                    }
                }
            }
        }
    }

    private void populateTables() {
        for (Token t : tokens)
            tokensTableModel.addRow(new Object[] { t.type, t.value, t.line });
        for (SymbolTableEntry e : symbolTableMap.values())
            symbolTableModel.addRow(new Object[] {
                    e.identifier, e.kind, e.type, e.value != null ? e.value : "N/A", e.size, e.dimension,
                    e.lineOfDeclaration == -1 ? "N/A" : e.lineOfDeclaration,
                    e.lineOfUsage == -1 ? "N/A" : e.lineOfUsage, e.address
            });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(WppScannerGUI::new);
    }

    private static class FindReplaceDialog extends JDialog {
        private final JTextPane textPane;
        private int searchStartIndex = 0;
        private final JTextField findField = new JTextField(20), replaceField = new JTextField(20);

        public FindReplaceDialog(JFrame parent, JTextPane textPane) {
            super(parent, "Find/Replace", false);
            this.textPane = textPane;
            JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
            inputPanel.add(new JLabel("Find:"));
            inputPanel.add(findField);
            inputPanel.add(new JLabel("Replace with:"));
            inputPanel.add(replaceField);
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton findNextButton = new JButton("Find Next"), replaceButton = new JButton("Replace"),
                    replaceAllButton = new JButton("Replace All"), closeButton = new JButton("Close");
            buttonPanel.add(findNextButton);
            buttonPanel.add(replaceButton);
            buttonPanel.add(replaceAllButton);
            buttonPanel.add(closeButton);
            setLayout(new BorderLayout(5, 5));
            add(inputPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(parent);
            findNextButton.addActionListener(e -> findNext());
            replaceButton.addActionListener(e -> replace());
            replaceAllButton.addActionListener(e -> replaceAll());
            closeButton.addActionListener(e -> dispose());
        }

        private void findNext() {
            String text = textPane.getText(), findText = findField.getText();
            if (findText.isEmpty())
                return;
            int foundIndex = text.indexOf(findText, searchStartIndex);
            if (foundIndex != -1) {
                textPane.select(foundIndex, foundIndex + findText.length());
                searchStartIndex = foundIndex + findText.length();
            } else {
                searchStartIndex = 0;
                JOptionPane.showMessageDialog(this, "No more occurrences.");
            }
        }

        private void replace() {
            String findText = findField.getText(), replaceText = replaceField.getText();
            if (findText.isEmpty())
                return;
            String selected = textPane.getSelectedText();
            if (selected != null && selected.equals(findText))
                textPane.replaceSelection(replaceText);
            findNext();
        }

        private void replaceAll() {
            String text = textPane.getText(), findText = findField.getText(), replaceText = replaceField.getText();
            if (findText.isEmpty())
                return;
            int count = 0, index = 0;
            while ((index = text.indexOf(findText, index)) != -1) {
                count++;
                text = text.substring(0, index) + replaceText + text.substring(index + findText.length());
                index += replaceText.length();
            }
            textPane.setText(text);
            searchStartIndex = 0;
            JOptionPane.showMessageDialog(this, "Replaced " + count + " occurrence(s).");
        }
    }
}