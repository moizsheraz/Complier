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
    private List<Token> tokens;
    private int currentIndex;
    private List<String> errors;
    private Stack<Set<String>> scopeStack;
    private Set<String> globalVariables;
    private Map<String, String> functionReturnTypes;
    private Map<String, Integer> functionParamCounts; // Track parameter counts for functions
    private boolean mainFunctionFound;
    private int mainFunctionLine; // Track line of main() for duplicate detection
    private Map<String, Integer> variableUsage; // Track variable usage for unused variable detection

    public SyntaxAnalyzer(List<Token> tokens) {
        this.tokens = tokens;
        this.currentIndex = 0;
        this.errors = new ArrayList<>();
        this.scopeStack = new Stack<>();
        this.globalVariables = new HashSet<>();
        this.functionReturnTypes = new HashMap<>();
        this.functionParamCounts = new HashMap<>();
        this.mainFunctionFound = false;
        this.mainFunctionLine = -1;
        this.variableUsage = new HashMap<>();
        scopeStack.push(globalVariables); // Global scope
    }

    public List<String> analyze() {
        while (currentIndex < tokens.size()) {
            Token token = tokens.get(currentIndex);
            if (isFunctionDeclaration()) {
                analyzeFunctionDeclaration();
            } else if (isDataType(token.value)) {
                analyzeVariableDeclaration();
            } else if (isIdentifier(token)) {
                Token nextToken = lookAhead();
                if (nextToken.type.equals("Operator")) {
                    if (nextToken.value.equals("=")) {
                        analyzeAssignment();
                    } else if (nextToken.value.equals("++") || nextToken.value.equals("--")) {
                        if (!isVariableDeclared(token.value)) {
                            errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                        }
                        currentIndex += 2; // Skip identifier and operator
                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                            currentIndex++;
                        } else {
                            errors.add("Syntax Error at Line " + token.line + ": Missing semicolon after increment/decrement");
                        }
                    } else if (nextToken.value.equals("<<") && token.value.equals("cout")) {
                        analyzeCoutStatement();
                    } else {
                        errors.add("Syntax Error at Line " + token.line + ": Invalid operator '" + nextToken.value + "' after identifier '" + token.value + "'");
                        currentIndex++;
                    }
                } else if (nextToken.type.equals("Separator") && nextToken.value.equals("(")) {
                    analyzeFunctionCall();
                } else {
                    errors.add("Syntax Error at Line " + token.line + ": Unexpected identifier '" + token.value + "' in statement");
                    currentIndex++;
                }
            } else if (token.value.equals("if")) {
                analyzeIfStatement();
            } else if (token.value.equals("cout")) {
                analyzeCoutStatement();
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
            } else if (token.type.equals("Separator") && token.value.equals(";")) {
                errors.add("Syntax Error at Line " + token.line + ": Stray semicolon");
                currentIndex++;
            } else if (token.type.equals("Operator")) {
                errors.add("Syntax Error at Line " + token.line + ": Unexpected operator '" + token.value + "' in global scope");
                currentIndex++;
            } else {
                errors.add("Syntax Error at Line " + token.line + ": Unexpected token '" + token.value + "'");
                currentIndex++;
            }
        }

        // Validate main() presence and variable usage
        if (!mainFunctionFound) {
            errors.add("Semantic Error at Line 1: No valid 'main' function found - program must define 'int main()' or 'int main(int argc, char* argv[])'");
        }
        // Check for unused variables
        for (Map.Entry<String, Integer> entry : variableUsage.entrySet()) {
            if (entry.getValue() == 0) {
                errors.add("Warning at Line 1: Variable '" + entry.getKey() + "' declared but never used");
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
            if (functionName.equals("main")) {
                if (mainFunctionFound) {
                    errors.add("Semantic Error at Line " + line + ": Duplicate 'main' function declaration; previous at Line " + mainFunctionLine);
                    currentIndex++;
                    return;
                }
                if (!returnType.equals("int")) {
                    errors.add("Syntax Error at Line " + line + ": 'main' function must return 'int'");
                } else {
                    mainFunctionFound = true;
                    mainFunctionLine = line;
                }
            }
            functionReturnTypes.put(functionName, returnType);
            currentIndex++; // consume identifier

            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("(")) {
                currentIndex++; // consume '('

                int paramCount = analyzeParameters(functionName);
                functionParamCounts.put(functionName, paramCount);

                if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(")")) {
                    errors.add("Syntax Error at Line " + line + ": Missing closing parenthesis in function declaration");
                } else {
                    currentIndex++; // consume ')'
                }

                if (currentIndex < tokens.size()) {
                    if (tokens.get(currentIndex).value.equals("{")) {
                        if (functionName.equals("main") && mainFunctionFound) {
                            // Ensure main() has a body
                            int startIndex = currentIndex;
                            skipBlock();
                            if (startIndex == currentIndex - 1) {
                                errors.add("Syntax Error at Line " + line + ": 'main' function must have a non-empty body");
                                mainFunctionFound = false;
                            }
                        } else {
                            skipBlock();
                        }
                    } else if (tokens.get(currentIndex).value.equals(";")) {
                        if (functionName.equals("main")) {
                            errors.add("Syntax Error at Line " + line + ": 'main' function must have a body, not just a prototype");
                            mainFunctionFound = false;
                        }
                        currentIndex++;
                    } else {
                        errors.add("Syntax Error at Line " + line + ": Expected '{' or ';' after function declaration");
                    }
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Expected '(' after function name");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Expected identifier after return type");
        }
    }

    private int analyzeParameters(String functionName) {
        int line = tokens.get(currentIndex).line;
        boolean expectParam = true;
        List<String> paramTypes = new ArrayList<>();
        int paramCount = 0;

        while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(")")) {
            Token token = tokens.get(currentIndex);

            if (token.value.equals(",")) {
                if (expectParam) {
                    errors.add("Syntax Error at Line " + line + ": Unexpected comma in parameter list");
                }
                expectParam = true;
                currentIndex++;
                continue;
            }

            if (expectParam) {
                if (isDataType(token.value)) {
                    String paramType = token.value;
                    if (paramType.equals("void") && !functionName.equals("main")) {
                        errors.add("Syntax Error at Line " + line + ": 'void' is not a valid parameter type");
                    }
                    paramTypes.add(paramType);
                    currentIndex++;

                    if (currentIndex < tokens.size() && isIdentifier(tokens.get(currentIndex))) {
                        String paramName = tokens.get(currentIndex).value;
                        if (isVariableDeclaredInCurrentScope(paramName)) {
                            errors.add("Syntax Error at Line " + line + ": Parameter '" + paramName + "' shadows variable in same scope");
                        }
                        addVariableToScope(paramName);
                        variableUsage.put(paramName, 0); // Initialize usage count
                        currentIndex++;
                        expectParam = false;
                        paramCount++;

                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                            currentIndex++;
                            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                                paramTypes.set(paramTypes.size() - 1, paramType + "[]");
                                currentIndex++;
                            } else {
                                errors.add("Syntax Error at Line " + line + ": Expected ']' in array parameter declaration");
                            }
                        }
                    } else {
                        errors.add("Syntax Error at Line " + line + ": Expected parameter name after type");
                    }
                } else {
                    errors.add("Syntax Error at Line " + line + ": Expected parameter type in function declaration");
                    currentIndex++;
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Expected comma between parameters");
                currentIndex++;
            }
        }

        // Validate main() parameters
        if (functionName.equals("main") && mainFunctionFound) {
            if (paramTypes.isEmpty()) {
                // Valid: int main()
            } else if (paramTypes.size() == 2 && paramTypes.get(0).equals("int") && paramTypes.get(1).equals("char[]")) {
                // Valid: int main(int argc, char* argv[])
            } else {
                errors.add("Syntax Error at Line " + line + ": Invalid parameters for 'main' function. Expected 'int main()' or 'int main(int argc, char* argv[])'");
                mainFunctionFound = false;
            }
        }

        return paramCount;
    }

    private boolean isDataType(String value) {
        return value.equals("int") || value.equals("float") || value.equals("double") ||
                value.equals("char") || value.equals("string") || value.equals("bool") || value.equals("void");
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
            if (isVariableDeclaredInCurrentScope(varName)) {
                errors.add("Syntax Error at Line " + line + ": Variable '" + varName + "' already declared in this scope");
            } else if (functionReturnTypes.containsKey(varName)) {
                errors.add("Syntax Error at Line " + line + ": Variable '" + varName + "' conflicts with function name");
            } else {
                addVariableToScope(varName);
                variableUsage.put(varName, 0); // Initialize usage count
            }
            currentIndex++;

            boolean isArray = false;
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                isArray = true;
                currentIndex++;
                if (currentIndex < tokens.size() && (tokens.get(currentIndex).type.startsWith("Literal") ||
                        isIdentifier(tokens.get(currentIndex)))) {
                    Token indexToken = tokens.get(currentIndex);
                    if (indexToken.type.equals("Literal (String)") || indexToken.type.equals("Literal (Char)")) {
                        errors.add("Syntax Error at Line " + line + ": Array index must be an integer");
                    }
                    currentIndex++;
                } else {
                    errors.add("Syntax Error at Line " + line + ": Expected array size after '['");
                }
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                    currentIndex++;
                } else {
                    errors.add("Syntax Error at Line " + line + ": Expected ']' after array size");
                }
            }

            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Operator")
                    && tokens.get(currentIndex).value.equals("=")) {
                currentIndex++;
                if (!isArray) {
                    int exprStart = currentIndex;
                    analyzeExpression(line, dataType);
                    if (currentIndex == exprStart) {
                        errors.add("Syntax Error at Line " + line + ": Expected value after '=' in variable initialization");
                    }
                } else {
                    errors.add("Syntax Error at Line " + line + ": Array initialization not supported in this context");
                    while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                        currentIndex++;
                    }
                }
            }

            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals(";")) {
                currentIndex++;
            } else {
                errors.add("Syntax Error at Line " + line + ": Missing semicolon after variable declaration");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Expected identifier after data type");
        }
    }

    private void analyzeExpression(int line, String expectedType) {
        int parenCount = 0;
        boolean lastWasOperator = false;
        int exprStart = currentIndex;

        while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
            Token token = tokens.get(currentIndex);
            if (token.type.equals("Separator")) {
                if (token.value.equals("(")) {
                    parenCount++;
                } else if (token.value.equals(")")) {
                    parenCount--;
                    if (parenCount < 0) {
                        errors.add("Syntax Error at Line " + line + ": Unmatched closing parenthesis in expression");
                        break;
                    }
                }
            } else if (token.type.equals("Operator")) {
                if (lastWasOperator) {
                    errors.add("Syntax Error at Line " + line + ": Invalid consecutive operators in expression");
                }
                lastWasOperator = true;
            } else if (isIdentifier(token)) {
                if (!isVariableDeclared(token.value)) {
                    errors.add("Syntax Error at Line " + line + ": Variable '" + token.value + "' used before declaration");
                } else {
                    variableUsage.merge(token.value, 1, Integer::sum); // Increment usage
                }
                lastWasOperator = false;
            } else if (token.type.startsWith("Literal")) {
                if (expectedType != null && !isTypeCompatible(expectedType, token)) {
                    errors.add("Type Error at Line " + line + ": Incompatible type '" + token.type + "' for expected type '" + expectedType + "'");
                }
                lastWasOperator = false;
            } else {
                errors.add("Syntax Error at Line " + line + ": Unexpected token '" + token.value + "' in expression");
            }
            currentIndex++;
        }

        if (parenCount > 0) {
            errors.add("Syntax Error at Line " + line + ": Unmatched opening parenthesis in expression");
        }
        if (lastWasOperator && currentIndex > exprStart) {
            errors.add("Syntax Error at Line " + line + ": Expression ends with an operator");
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
                errors.add("Syntax Error at Line " + line + ": Variable '" + varName + "' used before declaration");
            } else {
                variableUsage.merge(varName, 1, Integer::sum); // Increment usage
            }
            currentIndex++;
            boolean isArray = false;
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("[")) {
                isArray = true;
                currentIndex++;
                if (currentIndex < tokens.size() && (isIdentifier(tokens.get(currentIndex))
                        || tokens.get(currentIndex).type.startsWith("Literal"))) {
                    Token indexToken = tokens.get(currentIndex);
                    if (indexToken.type.equals("Literal (String)") || indexToken.type.equals("Literal (Char)")) {
                        errors.add("Syntax Error at Line " + line + ": Array index must be an integer");
                    }
                    currentIndex++;
                    if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("]")) {
                        currentIndex++;
                    } else {
                        errors.add("Syntax Error at Line " + line + ": Expected ']' after array index");
                    }
                } else {
                    errors.add("Syntax Error at Line " + line + ": Expected array index after '['");
                }
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Operator")
                    && tokens.get(currentIndex).value.equals("=")) {
                currentIndex++;
                int startIndex = currentIndex;
                analyzeExpression(line, null); // Type checking optional here
                if (currentIndex == startIndex) {
                    errors.add("Syntax Error at Line " + line + ": Expected value after '=' in assignment");
                }
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                    currentIndex++;
                } else {
                    errors.add("Syntax Error at Line " + line + ": Missing semicolon after assignment");
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Expected '=' in assignment");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Expected identifier in assignment");
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
                } else if (isIdentifier(token)) {
                    if (!isVariableDeclared(token.value)) {
                        errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                    } else {
                        variableUsage.merge(token.value, 1, Integer::sum);
                    }
                }
                currentIndex++;
            }
            if (openParens > 0) {
                errors.add("Syntax Error at Line " + line + ": Missing closing parenthesis in if statement");
            } else if (conditionStart == currentIndex - 1) {
                errors.add("Syntax Error at Line " + line + ": Empty condition in if statement");
            } else if (!hasComparison) {
                errors.add("Syntax Error at Line " + line + ": No comparison operator in if condition; expected boolean expression");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals("{")) {
                scopeStack.push(new HashSet<>());
                skipBlock();
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("else")) {
                    currentIndex++;
                    if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals("if")) {
                        analyzeIfStatement();
                    } else if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                            && tokens.get(currentIndex).value.equals("{")) {
                        scopeStack.push(new HashSet<>());
                        skipBlock();
                    } else if (currentIndex < tokens.size()) {
                        while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                            Token token = tokens.get(currentIndex);
                            if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                                errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value
                                        + "' used before declaration");
                            }
                            currentIndex++;
                        }
                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                            currentIndex++;
                        } else {
                            errors.add("Syntax Error at Line " + line + ": Missing semicolon after else statement");
                        }
                    } else {
                        errors.add("Syntax Error at Line " + line + ": Expected statement or '{' after 'else'");
                    }
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Expected '{' after if condition");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Missing opening parenthesis in if statement");
        }
    }

    private void analyzeForLoop() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                && tokens.get(currentIndex).value.equals("(")) {
            currentIndex++;
            scopeStack.push(new HashSet<>());
            if (currentIndex < tokens.size() && isDataType(tokens.get(currentIndex).value)) {
                analyzeVariableDeclaration();
            } else {
                while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                    Token token = tokens.get(currentIndex);
                    if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                        errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                    }
                    currentIndex++;
                }
                if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                    currentIndex++;
                } else {
                    errors.add("Syntax Error at Line " + line + ": Missing semicolon in for loop initialization");
                }
            }
            int conditionStart = currentIndex;
            boolean hasComparison = false;
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
                Token token = tokens.get(currentIndex);
                if (token.type.equals("Operator") && isComparisonOperator(token.value)) {
                    hasComparison = true;
                }
                if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                } else if (isIdentifier(token)) {
                    variableUsage.merge(token.value, 1, Integer::sum);
                }
                currentIndex++;
            }
            if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(";")) {
                errors.add("Syntax Error at Line " + line + ": Missing semicolon in for loop condition");
            } else {
                currentIndex++;
            }
            if (conditionStart == currentIndex - 1 && !hasComparison) {
                errors.add("Syntax Error at Line " + line + ": Empty or invalid condition in for loop");
            }
            while (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(")")) {
                Token token = tokens.get(currentIndex);
                if (isIdentifier(token) && !isVariableDeclared(token.value)) {
                    errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                } else if (isIdentifier(token)) {
                    variableUsage.merge(token.value, 1, Integer::sum);
                }
                currentIndex++;
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(")")) {
                currentIndex++;
                if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                        && tokens.get(currentIndex).value.equals("{")) {
                    skipBlock();
                } else {
                    errors.add("Syntax Error at Line " + line + ": Expected '{' after for loop");
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Missing closing parenthesis in for loop");
            }
            scopeStack.pop();
        } else {
            errors.add("Syntax Error at Line " + line + ": Missing opening parenthesis in for loop");
        }
    }

    private void analyzeWhileLoop() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;
        if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator") &&
                tokens.get(currentIndex).value.equals("(")) {
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
                } else if (isIdentifier(token)) {
                    if (!isVariableDeclared(token.value)) {
                        errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                    } else {
                        variableUsage.merge(token.value, 1, Integer::sum);
                    }
                }
                currentIndex++;
            }
            if (openParens > 0) {
                errors.add("Syntax Error at Line " + line + ": Missing closing parenthesis in while loop");
            } else if (conditionStart == currentIndex - 1) {
                errors.add("Syntax Error at Line " + line + ": Empty condition in while loop");
            } else if (!hasComparison) {
                errors.add("Syntax Error at Line " + line + ": No comparison operator in while condition; expected boolean expression");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator") &&
                    tokens.get(currentIndex).value.equals("{")) {
                scopeStack.push(new HashSet<>());
                skipBlock();
            } else {
                errors.add("Syntax Error at Line " + line + ": Expected '{' after while loop");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Missing opening parenthesis in while loop");
        }
    }

    private void analyzeCoutStatement() {
        int line = tokens.get(currentIndex).line;
        currentIndex++; // consume 'cout'

        while (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Operator") &&
               tokens.get(currentIndex).value.equals("<<")) {
            currentIndex++; // consume '<<'

            if (currentIndex >= tokens.size()) {
                errors.add("Syntax Error at Line " + line + ": Expected expression after '<<' in cout statement");
                return;
            }

            Token outputToken = tokens.get(currentIndex);
            if (outputToken.type.startsWith("Literal") ||
                (outputToken.type.equals("Identifier") && isVariableDeclared(outputToken.value)) ||
                outputToken.value.equals("endl")) {
                if (isIdentifier(outputToken)) {
                    variableUsage.merge(outputToken.value, 1, Integer::sum);
                }
                currentIndex++; // consume the output item
            } else {
                errors.add("Syntax Error at Line " + line + ": Invalid output item '" + outputToken.value + "' after '<<'");
                currentIndex++;
                return;
            }
        }

        if (currentIndex >= tokens.size() || !tokens.get(currentIndex).value.equals(";")) {
            errors.add("Syntax Error at Line " + line + ": Missing semicolon after cout statement");
        } else {
            currentIndex++;
        }
    }

    private void analyzeFunctionCall() {
        Token funcToken = tokens.get(currentIndex);
        int line = funcToken.line;
        String funcName = funcToken.value;
        if (funcName.equals("cout")) {
            analyzeCoutStatement();
            return;
        }
        if (!functionReturnTypes.containsKey(funcName)) {
            errors.add("Semantic Error at Line " + line + ": Function '" + funcName + "' called before declaration");
        }
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
                        errors.add("Syntax Error at Line " + line + ": Missing argument before comma in function call");
                    }
                    expectArg = true;
                } else if (isIdentifier(token) || token.type.startsWith("Literal")) {
                    if (!expectArg) {
                        errors.add("Syntax Error at Line " + line + ": Expected ',' between arguments in function call");
                    }
                    if (isIdentifier(token)) {
                        if (!isVariableDeclared(token.value)) {
                            errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                        } else {
                            variableUsage.merge(token.value, 1, Integer::sum);
                        }
                    }
                    expectArg = false;
                    argCount++;
                } else {
                    errors.add("Syntax Error at Line " + line + ": Invalid token '" + token.value + "' in function call argument");
                }
                currentIndex++;
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(")")) {
                currentIndex++;
                if (expectArg && argCount == 0) {
                    // Allow empty argument list
                } else if (expectArg) {
                    errors.add("Syntax Error at Line " + line + ": Missing argument after comma in function call");
                }
                // Check argument count
                if (functionReturnTypes.containsKey(funcName)) {
                    int expectedParams = functionParamCounts.getOrDefault(funcName, 0);
                    if (argCount != expectedParams) {
                        errors.add("Semantic Error at Line " + line + ": Function '" + funcName + "' expects " + expectedParams +
                                   " arguments but " + argCount + " were provided");
                    }
                }
            } else {
                errors.add("Syntax Error at Line " + line + ": Missing closing parenthesis in function call");
            }
            if (currentIndex < tokens.size() && tokens.get(currentIndex).type.equals("Separator")
                    && tokens.get(currentIndex).value.equals(";")) {
                currentIndex++;
            } else {
                errors.add("Syntax Error at Line " + line + ": Missing semicolon after function call");
            }
        } else {
            errors.add("Syntax Error at Line " + line + ": Expected '(' after function name");
        }
    }

    private void analyzeReturnStatement() {
        int line = tokens.get(currentIndex).line;
        currentIndex++;

        boolean inFunction = scopeStack.size() > 1;
        String expectedReturnType = "void";
        String functionName = null;

        if (inFunction) {
            for (int i = tokens.size() - 1; i >= 0; i--) {
                Token t = tokens.get(i);
                if (t.type.equals("Identifier") && functionReturnTypes.containsKey(t.value)) {
                    expectedReturnType = functionReturnTypes.get(t.value);
                    functionName = t.value;
                    break;
                }
            }
        }

        if (currentIndex < tokens.size() && !tokens.get(currentIndex).value.equals(";")) {
            if (expectedReturnType.equals("void")) {
                errors.add("Syntax Error at Line " + line + ": Void function should not return a value");
            }
            int startIndex = currentIndex;
            analyzeExpression(line, expectedReturnType);
            if (startIndex == currentIndex) {
                errors.add("Syntax Error at Line " + line + ": Expected return value for non-void function");
            }
            if (functionName != null && functionName.equals("main") && expectedReturnType.equals("int")) {
                Token lastToken = tokens.get(currentIndex - 1);
                if (!lastToken.type.equals("Literal (Int)")) {
                    errors.add("Syntax Error at Line " + line + ": 'main' function must return an integer value");
                }
            }
        } else if (!expectedReturnType.equals("void")) {
            errors.add("Syntax Error at Line " + line + ": Non-void function '" + (functionName != null ? functionName : "") +
                       "' must return a value");
        }

        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
            currentIndex++;
        } else {
            errors.add("Syntax Error at Line " + line + ": Missing semicolon after return statement");
        }
    }

    private void skipBlock() {
        currentIndex++; // Consume '{'
        scopeStack.push(new HashSet<>());
        int braceCount = 1;

        while (currentIndex < tokens.size() && braceCount > 0) {
            Token token = tokens.get(currentIndex);

            if (token.type.equals("Separator") && token.value.equals("{")) {
                braceCount++;
                skipBlock();
            } else if (token.type.equals("Separator") && token.value.equals("}")) {
                braceCount--;
                if (braceCount == 0) {
                    scopeStack.pop();
                    currentIndex++;
                    break;
                }
                currentIndex++;
            } else {
                if (isDataType(token.value)) {
                    analyzeVariableDeclaration();
                } else if (isIdentifier(token)) {
                    if (lookAhead().type.equals("Operator") && lookAhead().value.equals("=")) {
                        analyzeAssignment();
                    } else if (lookAhead().type.equals("Separator") && lookAhead().value.equals("(")) {
                        analyzeFunctionCall();
                    }else if (token.value.equals("cout")) {
    analyzeCoutStatement();
} else if (lookAhead().type.equals("Operator") &&
                            (lookAhead().value.equals("++") || lookAhead().value.equals("--"))) {
                        if (!isVariableDeclared(token.value)) {
                            errors.add("Syntax Error at Line " + token.line + ": Variable '" + token.value + "' used before declaration");
                        } else {
                            variableUsage.merge(token.value, 1, Integer::sum);
                        }
                        currentIndex += 2;
                        if (currentIndex < tokens.size() && tokens.get(currentIndex).value.equals(";")) {
                            currentIndex++;
                        } else {
                            errors.add("Syntax Error at Line " + token.line + ": Missing semicolon after increment/decrement");
                        }
                    } else if (token.value.equals("cout")) {
                        analyzeCoutStatement();
                    } else {
                        errors.add("Syntax Error at Line " + token.line + ": Invalid statement - unexpected identifier '" + token.value + "'");
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
                } else if (token.type.equals("Separator") && token.value.equals(";")) {
                    errors.add("Syntax Error at Line " + token.line + ": Stray semicolon in block");
                    currentIndex++;
                } else if (token.type.equals("Operator")) {
                    errors.add("Syntax Error at Line " + token.line + ": Unexpected operator '" + token.value + "' in block");
                    currentIndex++;
                } else {
                    errors.add("Syntax Error at Line " + token.line + ": Unexpected token '" + token.value + "' in block");
                    currentIndex++;
                }
            }
        }

        if (braceCount > 0) {
            errors.add("Syntax Error at Line " + tokens.get(currentIndex - 1).line + ": Missing closing brace '}'");
        }
    }

    private boolean isVariableDeclared(String varName) {
        for (Set<String> scope : scopeStack) {
            if (scope.contains(varName))
                return true;
        }
        return varName.equals("cout") || varName.equals("endl");
    }

    private boolean isVariableDeclaredInCurrentScope(String varName) {
        return scopeStack.peek().contains(varName);
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
            "cout", "endl"));
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
            UIManager.put("Table.gridColor", new Color(200, 200, 200));
            UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
            UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
            UIManager.put("TabbedPane.tabsOverlapBorder", true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Menu bar setup
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(236, 240, 245));
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(createMenuItem("New", "FileView.fileIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), e -> {
                    codeArea.setText("");
                    statusLabelLeft.setText("New file");
                    updateLineNumbers();
                    updateDocumentStats();
                }));
        fileMenu.add(createMenuItem("Open", "FileView.directoryIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), e -> openFile()));
        openRecentMenu = new JMenu("Open Recent");
        fileMenu.add(openRecentMenu);
        fileMenu.add(createMenuItem("Save", "FileView.fileIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), e -> saveFile()));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("Exit", null,
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), e -> System.exit(0)));

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(createMenuItem("Undo", "OptionPane.informationIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), e -> {
                    if (undoManager.canUndo())
                        undoManager.undo();
                }));
        editMenu.add(createMenuItem("Redo", "OptionPane.informationIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), e -> {
                    if (undoManager.canRedo())
                        undoManager.redo();
                }));

        JMenu compileMenu = new JMenu("Compile");
        compileMenu.add(createMenuItem("Run Scanner", "FileView.computerIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), e -> runScanner()));

        JMenu searchMenu = new JMenu("Search");
        searchMenu.add(createMenuItem("Find/Replace...", null,
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                e -> new FindReplaceDialog(this, codeArea).setVisible(true)));

        JMenu viewMenu = new JMenu("View");
        toggleDarkModeItem = createMenuItem("Toggle Dark Mode", "OptionPane.questionIcon",
                KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), e -> toggleDarkMode());
        viewMenu.add(toggleDarkModeItem);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(createMenuItem("About", "OptionPane.informationIcon", null, e -> showAboutDialog()));
        helpMenu.add(
                createMenuItem("Keyboard Shortcuts", "OptionPane.informationIcon", null, e -> showShortcutsHelp()));

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
        toolBar.setBackground(new Color(0, 122, 204));

        toolBar.add(createToolbarButton("Open", "FileView.directoryIcon", "Open", e -> openFile()));
        toolBar.addSeparator();
        toolBar.add(createToolbarButton("Save", "FileView.fileIcon", "Save", e -> saveFile()));
        toolBar.addSeparator();
        toolBar.add(createToolbarButton("Run", "FileView.computerIcon", "Run Scanner", e -> runScanner()));

        // Code area setup
       codeArea = new JTextPane();
        initializeStyles();
        String fontName = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .contains("JetBrains Mono") ? "JetBrains Mono" : Font.MONOSPACED;
        codeArea.setFont(new Font(fontName, Font.PLAIN, 16));
        codeArea.setBackground(new Color(245, 247, 250));
        codeArea.setDocument(new DefaultStyledDocument());
        codeArea.putClientProperty("caretWidth", 2);
        codeArea.getDocument().addDocumentListener(new SyntaxHighlightListener());
        codeArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        undoManager = new UndoManager();
        codeArea.getDocument().addUndoableEditListener(undoManager);

        // Line numbers
        lineNumbers = new JTextArea("1");
        lineNumbers.setBackground(new Color(230, 234, 240));
        lineNumbers.setForeground(new Color(80, 80, 80));
        lineNumbers.setEditable(false);
        lineNumbers.setFont(new Font(fontName, Font.PLAIN, 16));
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
        tokensTable.setBackground(new Color(245, 247, 250));
        tokensTable.setForeground(new Color(33, 33, 33));
        tokensTable.getTableHeader().setBackground(new Color(230, 234, 240));
        tokensTable.getTableHeader().setForeground(new Color(33, 33, 33));
        tokensTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        tokensTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tokensTable.setFillsViewportHeight(true);

        symbolTableModel = new DefaultTableModel(new Object[] { "Name", "Kind", "Type", "Value", "Size", "Dimension",
                "Line of Declaration", "Line of Usage", "Address" }, 0);
        symbolTable = new JTable(symbolTableModel);
        symbolTable.setRowHeight(25);
        symbolTable.setIntercellSpacing(new Dimension(10, 0));
        symbolTable.setShowGrid(true);
        symbolTable.setBackground(new Color(245, 247, 250));
        symbolTable.setForeground(new Color(33, 33, 33));
        symbolTable.getTableHeader().setBackground(new Color(230, 234, 240));
        symbolTable.getTableHeader().setForeground(new Color(33, 33, 33));
        symbolTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        symbolTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
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
        errorsTable.setBackground(new Color(245, 247, 250));
        errorsTable.getTableHeader().setBackground(new Color(230, 234, 240));
        errorsTable.getTableHeader().setForeground(new Color(33, 33, 33));
        errorsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        errorsTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        errorsTable.setFillsViewportHeight(true);

        errorsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(new Color(220, 53, 69));
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
        tablesTabbedPane.setBackground(new Color(236, 240, 245));
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
        statusPanel.setBackground(new Color(0, 122, 204));
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)));
        statusLabelLeft = new JLabel("Ready");
        statusLabelCenter = new JLabel("Words: 0 | Chars: 0 | Lines: 0");
        statusLabelCenter.setHorizontalAlignment(JLabel.CENTER);
        statusLabelRight = new JLabel("Dark Mode OFF");
        statusPanel.add(statusLabelLeft, BorderLayout.WEST);
        statusPanel.add(statusLabelCenter, BorderLayout.CENTER);
        statusPanel.add(statusLabelRight, BorderLayout.EAST);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(236, 240, 245));
        mainPanel.add(toolBar, BorderLayout.NORTH);
        mainPanel.add(mainSplit, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        getContentPane().add(mainPanel);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setVisible(true);
        updateLineNumbers();
        updateRecentMenu();
    }

    private JMenuItem createMenuItem(String text, String iconKey, KeyStroke accelerator, ActionListener listener) {
        Icon icon = null;
        if (iconKey != null) {
            icon = UIManager.getIcon(iconKey);
            if (icon != null) {
                System.out.println("Loaded built-in icon: " + iconKey);
            } else {
                System.err.println("Warning: Built-in icon not found: " + iconKey);
            }
        }
        JMenuItem item = new JMenuItem(text, icon);
        if (accelerator != null)
            item.setAccelerator(accelerator);
        if (listener != null)
            item.addActionListener(listener);
        return item;
    }

    private JButton createToolbarButton(String text, String iconKey, String tooltip, ActionListener action) {
        Icon icon = UIManager.getIcon(iconKey);
        JButton button = new JButton(text, icon);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.addActionListener(action);
        return button;
    }

    private void initializeStyles() {
        StyledDocument doc = codeArea.getStyledDocument();
        Style base = doc.getStyle(StyleContext.DEFAULT_STYLE);
        defaultStyle = doc.addStyle("default", base);
        StyleConstants.setForeground(defaultStyle, new Color(33, 33, 33));
        keywordStyle = doc.addStyle("keyword", base);
        StyleConstants.setForeground(keywordStyle, new Color(0, 102, 204));
        StyleConstants.setBold(keywordStyle, true);
        errorStyle = doc.addStyle("error", base);
        StyleConstants.setForeground(errorStyle, new Color(220, 53, 69));
        StyleConstants.setBold(errorStyle, true);
        StyleConstants.setFontSize(keywordStyle, 18);

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
                "<html><b>Wpp Compiler by Binary Brains</b><br><br>" +
                        "<table border='0' cellpadding='3'>" +
                        "<tr><td><font color='#0066cc'>Version:</font></td><td>1.2</td></tr>" +
                        "<tr><td><font color='#0066cc'>Features:</font></td><td>Syntax Analysis, Symbol Table, Error Checking</td></tr>"
                        +
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
        Color bg = darkMode ? new Color(30, 31, 34) : new Color(245, 247, 250);
        Color fg = darkMode ? new Color(200, 200, 200) : new Color(33, 33, 33);
        Color headerBg = darkMode ? new Color(45, 46, 50) : new Color(230, 234, 240);
        Color gridColor = darkMode ? new Color(60, 60, 60) : new Color(200, 200, 200);

        codeArea.setBackground(bg);
        codeArea.setForeground(fg);
        codeArea.setCaretColor(darkMode ? new Color(200, 200, 200) : new Color(33, 33, 33));
        StyleConstants.setForeground(defaultStyle, fg);
        StyleConstants.setForeground(keywordStyle, darkMode ? new Color(103, 140, 177) : new Color(0, 102, 204));
        StyleConstants.setFontSize(keywordStyle, 18);

        lineNumbers.setBackground(darkMode ? new Color(45, 46, 50) : new Color(230, 234, 240));
        lineNumbers.setForeground(darkMode ? new Color(150, 150, 150) : new Color(80, 80, 80));

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
        errorsTable.setForeground(new Color(220, 53, 69));
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

        // Predefine cout in symbol table
        SymbolTableEntry coutEntry = new SymbolTableEntry("cout", "stream", "ostream");
        coutEntry.lineOfDeclaration = 0; // Predefined
        symbolTableMap.put("cout", coutEntry);

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
                        if (currentType != null && !part.equals("cout") && !part.equals("endl"))
                            symbolTableMap.putIfAbsent(part,
                                    new SymbolTableEntry(part, "variable", currentType));
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
            int lineNumError = -1;
            if (error.startsWith("Line ")) {
                try {
                    lineNumError = Integer.parseInt(error.substring(5, error.indexOf(":")));
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    // Continue with -1
                }
            }
            errorsTableModel.addRow(new Object[] { lineNumError != -1 ? lineNumError : "N/A", error });
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
                    if (buffer.length() > 0) {
                        result.add(buffer.toString());
                        buffer.setLength(0);
                    }
                    buffer.append('"');
                    inString = true;
                } else if (ch == '\'') {
                    if (buffer.length() > 0) {
                        result.add(buffer.toString());
                        buffer.setLength(0);
                    }
                    buffer.append('\'');
                    inChar = true;
                } else if (Character.isWhitespace(ch)) {
                    if (buffer.length() > 0) {
                        result.add(buffer.toString());
                        buffer.setLength(0);
                    }
                } else {
                    // Check for multi-character operators
                    if (i + 1 < line.length()) {
                        String potentialOp = line.substring(i, i + 2);
                        if (OPERATORS.contains(potentialOp)) {
                            if (buffer.length() > 0) {
                                result.add(buffer.toString());
                                buffer.setLength(0);
                            }
                            result.add(potentialOp);
                            i++;
                            continue;
                        }
                    }

                    // Check for single-character operators/separators
                    String charStr = String.valueOf(ch);
                    if (SEPARATORS.contains(charStr) || OPERATORS.contains(charStr)) {
                        if (buffer.length() > 0) {
                            result.add(buffer.toString());
                            buffer.setLength(0);
                        }
                        result.add(charStr);
                    } else {
                        buffer.append(ch);
                    }
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
            if ("Identifier".equals(token.type) && !token.value.equals("cout") && !token.value.equals("endl")) {
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