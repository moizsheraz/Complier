import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.util.regex.*;

public class Main2 extends JFrame {
    private JTextPane codeEditor;
    private JTable symbolTable;
    private JTable tokenTable;
    private StatusBar statusBar;
    private Map<String, TokenInfo> tokenMap;
    private Map<String, SymbolInfo> symbols;
    private ArrayList<String> keywords;
    private StyledDocument document;
    private String currentFilePath;
    private boolean isDarkTheme = false;
    private Font editorFont;
    private int fontSize = 14;
    private Color keywordColor = new Color(86, 156, 214);
    private Color commentColor = new Color(106, 153, 85);
    private Color stringColor = new Color(206, 145, 120);
    private Color numberColor = new Color(181, 206, 168);
    private Color darkBackground = new Color(30, 30, 30);
    private Color darkForeground = new Color(212, 212, 212);

    // Theme colors
    private Color lightBackground = Color.WHITE;
    private Color lightForeground = Color.BLACK;
    private Color currentBackground;
    private Color currentForeground;
    private Color currentSelectionColor;
    private Color currentLineHighlight;

    public Main2() {
        setTitle("Java Code Compiler");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(createImageIcon("/icons/app_icon.png", "App Icon").getImage());

        // Initialize theme colors
        currentBackground = lightBackground;
        currentForeground = lightForeground;
        currentSelectionColor = new Color(173, 214, 255);
        currentLineHighlight = new Color(240, 240, 240);

        // Initialize data structures
        tokenMap = new HashMap<>();
        symbols = new HashMap<>();
        editorFont = new Font("JetBrains Mono", Font.PLAIN, fontSize);
        if (!isFontAvailable("JetBrains Mono")) {
            editorFont = new Font("Monospaced", Font.PLAIN, fontSize);
        }

        initializeKeywords();

        // Create the UI components
        createMenuBar();
        createToolBar();
        createEditorPane();
        createBottomPanel();
        createStatusBar();

        // Main panel layout - VS Code style (editor top, tables bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(500); // Initial divider position
        splitPane.setResizeWeight(0.7);    // Editor gets more space initially

        // Top panel with editor
        JPanel editorPanel = new JPanel(new BorderLayout());
        JScrollPane editorScrollPane = new JScrollPane(codeEditor);
        LineNumberComponent lineNumbers = new LineNumberComponent(codeEditor);
        editorScrollPane.setRowHeaderView(lineNumbers);
        editorPanel.add(editorScrollPane, BorderLayout.CENTER);

        // Bottom panel with tables
        JPanel bottomPanel = createBottomPanel();

        splitPane.setTopComponent(editorPanel);
        splitPane.setBottomComponent(bottomPanel);

        // Main content layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setVisible(true);
    }

private JPanel createBottomPanel() {
    JPanel bottomPanel = new JPanel(new BorderLayout());
    
    // Create tabbed pane for symbol table and tokens
    JTabbedPane bottomTabbedPane = new JTabbedPane();
    bottomTabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

    // Symbol Table
    String[] symbolColumns = { "Name", "Type", "Size", "Dimension", "Line of Declaration", "Line of Usage", "Address" };
    Object[][] symbolData = {};
    symbolTable = new JTable(new DefaultTableModel(symbolData, symbolColumns));
    styleTable(symbolTable);
    JScrollPane symbolScrollPane = new JScrollPane(symbolTable);
    symbolScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    bottomTabbedPane.addTab("Symbol Table", createImageIcon("/icons/symbol.png", "Symbol Table"), symbolScrollPane);

    // Token Table
    String[] tokenColumns = { "Token", "Type", "Count", "Line Numbers" };
    Object[][] tokenData = {};
    tokenTable = new JTable(new DefaultTableModel(tokenData, tokenColumns));
    styleTable(tokenTable);
    DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    tokenTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
    JScrollPane tokenScrollPane = new JScrollPane(tokenTable);
    tokenScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    bottomTabbedPane.addTab("Tokens", createImageIcon("/icons/token.png", "Tokens"), tokenScrollPane);

    // Syntax Analysis tab
    JPanel syntaxPanel = new JPanel(new BorderLayout());
    JTextArea syntaxOutput = new JTextArea();
    syntaxOutput.setEditable(false);
    syntaxOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
    JScrollPane syntaxScrollPane = new JScrollPane(syntaxOutput);
    syntaxScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    bottomTabbedPane.addTab("Syntax Analysis", createImageIcon("/icons/syntax.png", "Syntax Analysis"), syntaxScrollPane);

    // Add analyze button
    JButton analyzeButton = new JButton("Analyze Syntax");
    analyzeButton.addActionListener(e -> {
        String analysisResult = analyzeSyntax();
        syntaxOutput.setText(analysisResult);
    });
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(analyzeButton);
    syntaxPanel.add(syntaxScrollPane, BorderLayout.CENTER);
    syntaxPanel.add(buttonPanel, BorderLayout.SOUTH);

    // Add the tabbed pane to the bottom panel
    bottomPanel.add(bottomTabbedPane, BorderLayout.CENTER);
    return bottomPanel;
}

    private String analyzeSyntax() {
        StringBuilder result = new StringBuilder();
        String codeText = codeEditor.getText();
        String[] lines = codeText.split("\\r?\\n");
        
        result.append("=== Syntax Analysis Report ===\n\n");
        
        // Analyze each line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            result.append("Line ").append(i+1).append(": ");
            
            if (isDeclarationStatement(line)) {
                result.append("Variable Declaration - ").append(analyzeDeclaration(line)).append("\n");
            } 
            else if (isInitializationStatement(line)) {
                result.append("Variable Initialization - ").append(analyzeInitialization(line)).append("\n");
            } 
            else if (isAssignmentStatement(line)) {
                result.append("Assignment Statement - ").append(analyzeAssignment(line)).append("\n");
            } 
            else if (isIfStatement(line)) {
                result.append("If Statement - ").append(analyzeIfStatement(line)).append("\n");
            } 
            else if (isIfElseStatement(line)) {
                result.append("If-Else Statement - ").append(analyzeIfElseStatement(line)).append("\n");
            }
            else {
                result.append("Other Statement\n");
            }
        }
        
        return result.toString();
    }

    // New method to check for syntax errors
    private ArrayList<SyntaxError> checkSyntaxErrors() {
        ArrayList<SyntaxError> errors = new ArrayList<>();
        String codeText = codeEditor.getText();
        String[] lines = codeText.split("\\r?\\n");
        
        // Analyze each line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            // Check for missing semicolons
            if (!line.endsWith(";") && !line.endsWith("{") && !line.endsWith("}") 
                && !line.startsWith("if") && !line.startsWith("else") 
                && !line.startsWith("for") && !line.startsWith("while")
                && !line.startsWith("//") && !line.startsWith("/*")
                && !line.endsWith("*/") && line.length() > 0) {
                errors.add(new SyntaxError(i+1, "Missing semicolon at the end of statement"));
            }
            
            // Check for unbalanced parentheses
            int openParenCount = 0;
            int closeParenCount = 0;
            for (char c : line.toCharArray()) {
                if (c == '(') openParenCount++;
                if (c == ')') closeParenCount++;
            }
            if (openParenCount != closeParenCount) {
                errors.add(new SyntaxError(i+1, "Unbalanced parentheses"));
            }
            
            // Check for unbalanced braces
            int openBraceCount = 0;
            int closeBraceCount = 0;
            for (char c : line.toCharArray()) {
                if (c == '{') openBraceCount++;
                if (c == '}') closeBraceCount++;
            }
            if (openBraceCount != closeBraceCount) {
                errors.add(new SyntaxError(i+1, "Unbalanced braces"));
            }
            
            // Check for invalid variable declarations
            if (line.matches(".*\\b(int|double|String|char|boolean|float|long)\\b.*") && 
                !isDeclarationStatement(line) && !isInitializationStatement(line)) {
                errors.add(new SyntaxError(i+1, "Invalid variable declaration syntax"));
            }
            
            // Check for invalid assignment statements
            if (line.contains("=") && !line.contains("==") && !line.contains("!=") && 
                !line.contains("<=") && !line.contains(">=") && 
                !isInitializationStatement(line) && !isAssignmentStatement(line)) {
                errors.add(new SyntaxError(i+1, "Invalid assignment syntax"));
            }
        }
        
        return errors;
    }

    // Class to represent a syntax error
    private class SyntaxError {
        private int lineNumber;
        private String message;
        
        public SyntaxError(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return "Line " + lineNumber + ": " + message;
        }
    }

    private String analyzeDeclaration(String line) {
        Pattern pattern = Pattern.compile(
            "\\b(int|double|String|char|boolean|float|long)(\\s*\\[\\s*\\])*\\s+" +
            "([a-zA-Z_][a-zA-Z0-9_]*\\s*(\\[\\s*\\])*(\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*(\\[\\s*\\])*)*)\\s*;");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String type = matcher.group(1) + (matcher.group(2) != null ? matcher.group(2).replaceAll("\\s", "") : "");
            String varsList = matcher.group(3).replaceAll("\\s*\\[\\s*\\]", "[]");
            String[] varNames = varsList.split("\\s*,\\s*");
            
            return "Declared " + varNames.length + " variable(s) of type " + type;
        }
        return "Invalid declaration syntax";
    }

    private String analyzeInitialization(String line) {
        Pattern pattern = Pattern.compile(
            "\\b(int|double|String|char|boolean|float|long)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(\\s*\\[\\s*\\])?\\s*=\\s*(.*?)\\s*;");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            boolean isArray = matcher.group(3) != null;
            String value = matcher.group(4);
            
            return "Initialized " + (isArray ? "array " : "") + "variable '" + name + 
                   "' of type " + type + " with value: " + value;
        }
        return "Invalid initialization syntax";
    }

private String analyzeAssignment(String line) {
    Pattern pattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*?)\\s*;");
    Matcher matcher = pattern.matcher(line);
    
    if (matcher.find()) {
        String varName = matcher.group(1);
        String value = matcher.group(2);
        
        if (symbols.containsKey(varName)) {
            return "Assignment to variable '" + varName + "' (" + symbols.get(varName).getType() + 
                   ") with value: " + value;
        } else {
            return "Assignment to undeclared variable '" + varName + "'";
        }
    }
    return "Invalid assignment syntax";
}

private String analyzeIfStatement(String line) {
    Pattern pattern = Pattern.compile("if\\s*\\$\\(([^)]+)\\)\\$");
    Matcher matcher = pattern.matcher(line);
    
    if (matcher.find()) {
        String condition = matcher.group(1);
        return "Condition: " + condition;
    }
    return "Invalid if statement syntax";
}

private String analyzeIfElseStatement(String line) {
    if (line.contains("else if")) {
        Pattern pattern = Pattern.compile("else\\s+if\\s*\\$\\(([^)]+)\\)\\$");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String condition = matcher.group(1);
            return "Else-If with condition: " + condition;
        }
    } else if (line.contains("else")) {
        return "Else statement";
    }
    return "Invalid if-else syntax";
}

private boolean isIfElseStatement(String line) {
    return line.trim().startsWith("else if") || line.trim().startsWith("else");
}


    // All other methods remain exactly the same as in your original code
    // Only the layout-related methods were changed
    
    private boolean isFontAvailable(String fontName) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        for (String name : fontNames) {
            if (name.equals(fontName)) {
                return true;
            }
        }
        return false;
    }

    private ImageIcon createImageIcon(String path, String description) {
        java.net.URL imgURL = getClass().getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL, description);
        } else {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.dispose();
            return new ImageIcon(image, description);
        }
    }

    private void saveFileAs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save As");
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            currentFilePath = file.getAbsolutePath();
            saveFile();
        }
    }

    private void showFindReplaceDialog() {
        JDialog dialog = new JDialog(this, "Find/Replace", false);
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

        JTextField findField = new JTextField(20);
        JTextField replaceField = new JTextField(20);
        JCheckBox caseSensitive = new JCheckBox("Match case");

        JButton findBtn = new JButton("Find");
        JButton replaceBtn = new JButton("Replace");
        JButton replaceAllBtn = new JButton("Replace All");

        findBtn.addActionListener(e -> {
            String text = codeEditor.getText();
            String target = findField.getText();
            int start = codeEditor.getSelectionEnd();

            if (caseSensitive.isSelected()) {
                start = text.indexOf(target, start);
            } else {
                start = text.toLowerCase().indexOf(target.toLowerCase(), start);
            }

            if (start != -1) {
                codeEditor.select(start, start + target.length());
            } else {
                JOptionPane.showMessageDialog(dialog, "No more occurrences found");
            }
        });

        replaceBtn.addActionListener(e -> {
            if (codeEditor.getSelectedText() != null) {
                try {
                    int start = codeEditor.getSelectionStart();
                    int length = codeEditor.getSelectionEnd() - start;
                    document.remove(start, length);
                    document.insertString(start, replaceField.getText(), null);
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }
        });

        replaceAllBtn.addActionListener(e -> {
            String content = codeEditor.getText();
            String replaced = caseSensitive.isSelected()
                    ? content.replace(findField.getText(), replaceField.getText())
                    : content.replaceAll("(?i)" + Pattern.quote(findField.getText()), replaceField.getText());

            codeEditor.setText(replaced);
        });

        panel.add(new JLabel("Find:"));
        panel.add(findField);
        panel.add(new JLabel("Replace:"));
        panel.add(replaceField);
        panel.add(caseSensitive);

        JPanel btnPanel = new JPanel();
        btnPanel.add(findBtn);
        btnPanel.add(replaceBtn);
        btnPanel.add(replaceAllBtn);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void changeFontSize(int delta) {
        fontSize += delta;
        editorFont = editorFont.deriveFont((float) fontSize);
        codeEditor.setFont(editorFont);
    }

    private void resetFontSize() {
        fontSize = 14;
        editorFont = editorFont.deriveFont((float) fontSize);
        codeEditor.setFont(editorFont);
    }

    private Color[] themePresets = {
            // Light Theme
            Color.WHITE, Color.BLACK, new Color(173, 214, 255), new Color(240, 240, 240),
            // Dark Theme
            new Color(30, 30, 30), new Color(212, 212, 212), new Color(38, 79, 120), new Color(60, 60, 60),
            // New Purple Theme
            new Color(45, 45, 65), new Color(220, 220, 240), new Color(147, 112, 219), new Color(80, 80, 100)
    };

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        int themeIndex = isDarkTheme ? 4 : 0;

        currentBackground = themePresets[themeIndex];
        currentForeground = themePresets[themeIndex + 1];
        currentSelectionColor = themePresets[themeIndex + 2];
        currentLineHighlight = themePresets[themeIndex + 3];

        // Update syntax colors for dark theme
        if (isDarkTheme) {
            keywordColor = new Color(104, 151, 187); // Soft blue
            commentColor = new Color(106, 153, 85); // Muted green
            stringColor = new Color(200, 120, 150); // Rose pink
            numberColor = new Color(181, 206, 168); // Pastel green
        } else {
            keywordColor = new Color(86, 156, 214); // VS Code blue
            commentColor = new Color(106, 153, 85);
            stringColor = new Color(163, 21, 21);
            numberColor = new Color(128, 0, 128);
        }

        applyTheme();
    }

    private void applyTheme() {
        codeEditor.setBackground(currentBackground);
        codeEditor.setForeground(currentForeground);
        codeEditor.setCaretColor(currentForeground);
        codeEditor.setSelectionColor(currentSelectionColor);

        Color tableBg = currentBackground;
        Color tableFg = currentForeground;
        Color gridColor = isDarkTheme ? new Color(80, 80, 100) : new Color(200, 200, 200);

        // Apply to tables
        styleTable(symbolTable, tableBg, tableFg, gridColor);
        styleTable(tokenTable, tableBg, tableFg, gridColor);

        // Update status bar
        statusBar.setBackground(isDarkTheme ? new Color(60, 60, 80) : new Color(240, 240, 240));
        statusBar.setForeground(currentForeground);

        // Update line numbers
        codeEditor.repaint();
    }

    private void styleTable(JTable table, Color bg, Color fg, Color grid) {
        table.setBackground(bg);
        table.setForeground(fg);
        table.setGridColor(grid);
        table.getTableHeader().setBackground(isDarkTheme ? new Color(70, 70, 90) : new Color(220, 220, 220));
        table.getTableHeader().setForeground(fg);
        table.setSelectionBackground(currentSelectionColor);
    }

    private void showHelpContents() {
        JTextArea helpText = new JTextArea(
                "Keyboard Shortcuts:\n"
                        + "Ctrl+N - New File\n"
                        + "Ctrl+O - Open File\n"
                        + "Ctrl+S - Save File\n"
                        + "Ctrl+Z - Undo\n"
                        + "Ctrl+Y - Redo\n"
                        + "Ctrl+F - Find\n"
                        + "F9 - Tokenize Code\n\n"
                        + "Features:\n"
                        + "- Dark/Light Theme Toggle (Ctrl+Shift+T)\n"
                        + "- Syntax Highlighting\n"
                        + "- Symbol Table Generation\n"
                        + "- Auto-Indentation");

        JOptionPane.showMessageDialog(this, helpText, "Help Guide", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initializeKeywords() {
        keywords = new ArrayList<>(Arrays.asList(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "package",
                "private", "protected", "public", "return", "short", "static", "strictfp", "super",
                "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
                "volatile", "while", "true", "false", "null"));
    }

    private void createMenuBar() {
        // Create the menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(0, 122, 204)); // Blue background for the menu bar
    
        // File Menu
        ImageIcon fileIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledFileIcon = fileIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenu fileMenu = new JMenu("File");
        fileMenu.setIcon(new ImageIcon(scaledFileIcon));
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fileMenu.setMnemonic(KeyEvent.VK_F);
        fileMenu.setBackground(new Color(0, 122, 204)); // Blue background
        fileMenu.setForeground(Color.WHITE); // White text for contrast
        fileMenu.setOpaque(true); // Make background visible
    
        // New Menu Item
        ImageIcon newIcon = new ImageIcon(getClass().getResource("/icons/new.png"));
        Image scaledNewIcon = newIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem newMenuItem = new JMenuItem("New", new ImageIcon(scaledNewIcon));
        newMenuItem.setMnemonic(KeyEvent.VK_N);
        newMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newMenuItem.addActionListener(e -> createNewFile());
        newMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        newMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        newMenuItem.setForeground(Color.WHITE); // White text
        newMenuItem.setOpaque(true);
    
        // Open Menu Item
        ImageIcon openIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledOpenIcon = openIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem openMenuItem = new JMenuItem("Open", new ImageIcon(scaledOpenIcon));
        openMenuItem.setMnemonic(KeyEvent.VK_O);
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openMenuItem.addActionListener(e -> openFile());
        openMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        openMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        openMenuItem.setForeground(Color.WHITE); // White text
        openMenuItem.setOpaque(true);
    
        // Save Menu Item
        ImageIcon saveIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledSaveIcon = saveIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem saveMenuItem = new JMenuItem("Save", new ImageIcon(scaledSaveIcon));
        saveMenuItem.setMnemonic(KeyEvent.VK_S);
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveMenuItem.addActionListener(e -> saveFile());
        saveMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        saveMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        saveMenuItem.setForeground(Color.WHITE); // White text
        saveMenuItem.setOpaque(true);
    
        // Save As Menu Item
        ImageIcon saveAsIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledSaveAsIcon = saveAsIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem saveAsMenuItem = new JMenuItem("Save As", new ImageIcon(scaledSaveAsIcon));
        saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsMenuItem.addActionListener(e -> saveFileAs());
        saveAsMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        saveAsMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        saveAsMenuItem.setForeground(Color.WHITE); // White text
        saveAsMenuItem.setOpaque(true);
    
        // Exit Menu Item
        ImageIcon exitIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledExitIcon = exitIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem exitMenuItem = new JMenuItem("Exit", new ImageIcon(scaledExitIcon));
        exitMenuItem.setMnemonic(KeyEvent.VK_X);
        exitMenuItem.addActionListener(e -> System.exit(0));
        exitMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        exitMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        exitMenuItem.setForeground(Color.WHITE); // White text
        exitMenuItem.setOpaque(true);
    
        fileMenu.add(newMenuItem);
        fileMenu.add(openMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
    
        // Edit Menu
        ImageIcon editIcon = new ImageIcon(getClass().getResource("/icons/edit.png"));
        Image scaledEditIcon = editIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenu editMenu = new JMenu("Edit");
        editMenu.setIcon(new ImageIcon(scaledEditIcon));
        editMenu.setMnemonic(KeyEvent.VK_E);
        editMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        editMenu.setBackground(new Color(0, 122, 204)); // Blue background
        editMenu.setForeground(Color.WHITE); // White text
        editMenu.setOpaque(true);
    
        // Undo Menu Item
        ImageIcon undoIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledUndoIcon = undoIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem undoMenuItem = new JMenuItem("Undo", new ImageIcon(scaledUndoIcon));
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoMenuItem.addActionListener(e -> {
            // Implement undo functionality here
        });
        undoMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        undoMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        undoMenuItem.setForeground(Color.WHITE); // White text
        undoMenuItem.setOpaque(true);
    
        // Redo Menu Item
        ImageIcon redoIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledRedoIcon = redoIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem redoMenuItem = new JMenuItem("Redo", new ImageIcon(scaledRedoIcon));
        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        redoMenuItem.addActionListener(e -> {
            // Implement redo functionality here
        });
        redoMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        redoMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        redoMenuItem.setForeground(Color.WHITE); // White text
        redoMenuItem.setOpaque(true);
    
        // Cut Menu Item
        ImageIcon cutIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledCutIcon = cutIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem cutMenuItem = new JMenuItem("Cut", new ImageIcon(scaledCutIcon));
        cutMenuItem.setMnemonic(KeyEvent.VK_T);
        cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cutMenuItem.addActionListener(e -> codeEditor.cut());
        cutMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        cutMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        cutMenuItem.setForeground(Color.WHITE); // White text
        cutMenuItem.setOpaque(true);
    
        // Copy Menu Item
        ImageIcon copyIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledCopyIcon = copyIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem copyMenuItem = new JMenuItem("Copy", new ImageIcon(scaledCopyIcon));
        copyMenuItem.setMnemonic(KeyEvent.VK_C);
        copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyMenuItem.addActionListener(e -> codeEditor.copy());
        copyMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        copyMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        copyMenuItem.setForeground(Color.WHITE); // White text
        copyMenuItem.setOpaque(true);
    
        // Paste Menu Item
        ImageIcon pasteIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledPasteIcon = pasteIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem pasteMenuItem = new JMenuItem("Paste", new ImageIcon(scaledPasteIcon));
        pasteMenuItem.setMnemonic(KeyEvent.VK_P);
        pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        pasteMenuItem.addActionListener(e -> codeEditor.paste());
        pasteMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        pasteMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        pasteMenuItem.setForeground(Color.WHITE); // White text
        pasteMenuItem.setOpaque(true);
    
        // Select All Menu Item
        ImageIcon selectAllIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledSelectAllIcon = selectAllIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem selectAllMenuItem = new JMenuItem("Select All", new ImageIcon(scaledSelectAllIcon));
        selectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        selectAllMenuItem.addActionListener(e -> codeEditor.selectAll());
        selectAllMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        selectAllMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        selectAllMenuItem.setForeground(Color.WHITE); // White text
        selectAllMenuItem.setOpaque(true);
    
        // Find/Replace Menu Item
        ImageIcon findIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledFindIcon = findIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem findReplaceMenuItem = new JMenuItem("Find/Replace", new ImageIcon(scaledFindIcon));
        findReplaceMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        findReplaceMenuItem.addActionListener(e -> showFindReplaceDialog());
        findReplaceMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        findReplaceMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        findReplaceMenuItem.setForeground(Color.WHITE); // White text
        findReplaceMenuItem.setOpaque(true);
    
        editMenu.add(undoMenuItem);
        editMenu.add(redoMenuItem);
        editMenu.addSeparator();
        editMenu.add(cutMenuItem);
        editMenu.add(copyMenuItem);
        editMenu.add(pasteMenuItem);
        editMenu.add(selectAllMenuItem);
        editMenu.addSeparator();
        editMenu.add(findReplaceMenuItem);
    
        // View Menu
        ImageIcon viewIcon = new ImageIcon(getClass().getResource("/icons/view.png"));
        Image scaledViewIcon = viewIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenu viewMenu = new JMenu("View");
        viewMenu.setIcon(new ImageIcon(scaledViewIcon));
        viewMenu.setMnemonic(KeyEvent.VK_V);
        viewMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        viewMenu.setBackground(new Color(0, 122, 204)); // Blue background
        viewMenu.setForeground(Color.WHITE); // White text
        viewMenu.setOpaque(true);
    
        // Zoom In Menu Item
        ImageIcon zoomInIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledZoomInIcon = zoomInIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem zoomInMenuItem = new JMenuItem("Zoom In", new ImageIcon(scaledZoomInIcon));
        zoomInMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK));
        zoomInMenuItem.addActionListener(e -> changeFontSize(1));
        zoomInMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        zoomInMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        zoomInMenuItem.setForeground(Color.WHITE); // White text
        zoomInMenuItem.setOpaque(true);
    
        // Zoom Out Menu Item
        ImageIcon zoomOutIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledZoomOutIcon = zoomOutIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem zoomOutMenuItem = new JMenuItem("Zoom Out", new ImageIcon(scaledZoomOutIcon));
        zoomOutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutMenuItem.addActionListener(e -> changeFontSize(-1));
        zoomOutMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        zoomOutMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        zoomOutMenuItem.setForeground(Color.WHITE); // White text
        zoomOutMenuItem.setOpaque(true);
    
        // Reset Zoom Menu Item
        ImageIcon resetZoomIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledResetZoomIcon = resetZoomIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem resetZoomMenuItem = new JMenuItem("Reset Zoom", new ImageIcon(scaledResetZoomIcon));
        resetZoomMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        resetZoomMenuItem.addActionListener(e -> resetFontSize());
        resetZoomMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        resetZoomMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        resetZoomMenuItem.setForeground(Color.WHITE); // White text
        resetZoomMenuItem.setOpaque(true);
    
        // Toggle Theme Menu Item
        ImageIcon themeIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledThemeIcon = themeIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JCheckBoxMenuItem toggleThemeMenuItem = new JCheckBoxMenuItem("Dark Theme", new ImageIcon(scaledThemeIcon));
        toggleThemeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        toggleThemeMenuItem.addActionListener(e -> toggleTheme());
        toggleThemeMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        toggleThemeMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        toggleThemeMenuItem.setForeground(Color.WHITE); // White text
        toggleThemeMenuItem.setOpaque(true);
    
        viewMenu.add(zoomInMenuItem);
        viewMenu.add(zoomOutMenuItem);
        viewMenu.add(resetZoomMenuItem);
        viewMenu.addSeparator();
        viewMenu.add(toggleThemeMenuItem);
    
        // Compile Menu
        ImageIcon compileIcon = new ImageIcon(getClass().getResource("/icons/play.png"));
        Image scaledCompileIcon = compileIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenu compileMenu = new JMenu("Compile");
        compileMenu.setIcon(new ImageIcon(scaledCompileIcon));
        compileMenu.setMnemonic(KeyEvent.VK_C);
        compileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        compileMenu.setBackground(new Color(0, 122, 204)); // Blue background
        compileMenu.setForeground(Color.WHITE); // White text
        compileMenu.setOpaque(true);
    
        // Tokenize Menu Item
        ImageIcon tokenizeIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledTokenizeIcon = tokenizeIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem tokenizeMenuItem = new JMenuItem("Tokenize", new ImageIcon(scaledTokenizeIcon));
        tokenizeMenuItem.setMnemonic(KeyEvent.VK_T);
        tokenizeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        tokenizeMenuItem.addActionListener(e -> tokenizeCode());
        tokenizeMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        tokenizeMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        tokenizeMenuItem.setForeground(Color.WHITE); // White text
        tokenizeMenuItem.setOpaque(true);
    
        // Syntax Analysis Menu Item
        ImageIcon syntaxIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledSyntaxIcon = syntaxIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem syntaxMenuItem = new JMenuItem("Syntax Analysis", new ImageIcon(scaledSyntaxIcon));
        syntaxMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0));
        syntaxMenuItem.addActionListener(e -> {
            String analysisResult = analyzeSyntax();
            JOptionPane.showMessageDialog(this, analysisResult, "Syntax Analysis", JOptionPane.INFORMATION_MESSAGE);
        });
        syntaxMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        syntaxMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        syntaxMenuItem.setForeground(Color.WHITE); // White text
        syntaxMenuItem.setOpaque(true);

        compileMenu.add(tokenizeMenuItem);
        compileMenu.add(syntaxMenuItem);

        // Help Menu
        ImageIcon helpIcon = new ImageIcon(getClass().getResource("/icons/help.png"));
        Image scaledHelpIcon = helpIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setIcon(new ImageIcon(scaledHelpIcon));
        helpMenu.setMnemonic(KeyEvent.VK_H);
        helpMenu.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        helpMenu.setBackground(new Color(0, 122, 204)); // Blue background
        helpMenu.setForeground(Color.WHITE); // White text
        helpMenu.setOpaque(true);

        // Help Contents Menu Item
        ImageIcon helpContentsIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledHelpContentsIcon = helpContentsIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem helpContentsMenuItem = new JMenuItem("Help Contents", new ImageIcon(scaledHelpContentsIcon));
        helpContentsMenuItem.addActionListener(e -> showHelpContents());
        helpContentsMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        helpContentsMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        helpContentsMenuItem.setForeground(Color.WHITE); // White text
        helpContentsMenuItem.setOpaque(true);

        // About Menu Item
        ImageIcon aboutIcon = new ImageIcon(getClass().getResource("/icons/file.png"));
        Image scaledAboutIcon = aboutIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        JMenuItem aboutMenuItem = new JMenuItem("About", new ImageIcon(scaledAboutIcon));
        aboutMenuItem.setMnemonic(KeyEvent.VK_A);
        aboutMenuItem.addActionListener(e -> showAboutDialog());
        aboutMenuItem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        aboutMenuItem.setBackground(new Color(0, 122, 204)); // Blue background
        aboutMenuItem.setForeground(Color.WHITE); // White text
        aboutMenuItem.setOpaque(true);

        helpMenu.add(helpContentsMenuItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(compileMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEtchedBorder());
        toolBar.setFont(new Font("Segoe UI", Font.PLAIN, 18));

        // New button
        JButton newButton = new JButton(createImageIcon("/icons/new.png", "New File"));
        newButton.setToolTipText("New File (Ctrl+N)");
        newButton.setFocusable(false);
        newButton.addActionListener(e -> createNewFile());
        

        // Open button
        JButton openButton = new JButton(createImageIcon("/icons/open.png", "Open File"));
        openButton.setToolTipText("Open File (Ctrl+O)");
        openButton.setFocusable(false);
        openButton.addActionListener(e -> openFile());

        // Save button
        JButton saveButton = new JButton(createImageIcon("/icons/save.png", "Save File"));
        saveButton.setToolTipText("Save File (Ctrl+S)");
        saveButton.setFocusable(false);
        saveButton.addActionListener(e -> saveFile());

        // Cut button
        JButton cutButton = new JButton(createImageIcon("/icons/cut.png", "Cut"));
        cutButton.setToolTipText("Cut (Ctrl+X)");
        cutButton.setFocusable(false);
        cutButton.addActionListener(e -> codeEditor.cut());

        // Copy button
        JButton copyButton = new JButton(createImageIcon("/icons/copy.png", "Copy"));
        copyButton.setToolTipText("Copy (Ctrl+C)");
        copyButton.setFocusable(false);
        copyButton.addActionListener(e -> codeEditor.copy());

        // Paste button
        JButton pasteButton = new JButton(createImageIcon("/icons/paste.png", "Paste"));
        pasteButton.setToolTipText("Paste (Ctrl+V)");
        pasteButton.setFocusable(false);
        pasteButton.addActionListener(e -> codeEditor.paste());

        // Undo button
        JButton undoButton = new JButton(createImageIcon("/icons/undo.png", "Undo"));
        undoButton.setToolTipText("Undo (Ctrl+Z)");
        undoButton.setFocusable(false);
        undoButton.addActionListener(e -> {
            // Implement undo functionality
        });

        // Redo button
        JButton redoButton = new JButton(createImageIcon("/icons/redo.png", "Redo"));
        redoButton.setToolTipText("Redo (Ctrl+Y)");
        redoButton.setFocusable(false);
        redoButton.addActionListener(e -> {
            // Implement redo functionality
        });

        // Find button
        JButton findButton = new JButton(createImageIcon("/icons/find.png", "Find"));
        findButton.setToolTipText("Find/Replace (Ctrl+F)");
        findButton.setFocusable(false);
        findButton.addActionListener(e -> showFindReplaceDialog());

        // Tokenize button
        JButton tokenizeButton = new JButton(createImageIcon("/icons/tokenize.png", "Tokenize"));
        tokenizeButton.setToolTipText("Tokenize Code (F9)");
        tokenizeButton.setFocusable(false);
        tokenizeButton.addActionListener(e -> tokenizeCode());

        // Syntax Analysis button
        JButton syntaxButton = new JButton(createImageIcon("/icons/syntax.png", "Syntax Analysis"));
        syntaxButton.setToolTipText("Analyze Syntax (F10)");
        syntaxButton.setFocusable(false);
        syntaxButton.addActionListener(e -> {
            String analysisResult = analyzeSyntax();
            JOptionPane.showMessageDialog(this, analysisResult, "Syntax Analysis", JOptionPane.INFORMATION_MESSAGE);
        });

        toolBar.add(newButton);
        toolBar.add(openButton);
        toolBar.add(saveButton);
        toolBar.addSeparator();
        toolBar.add(cutButton);
        toolBar.add(copyButton);
        toolBar.add(pasteButton);
        toolBar.addSeparator();
        toolBar.add(undoButton);
        toolBar.add(redoButton);
        toolBar.addSeparator();
        toolBar.add(findButton);
        toolBar.addSeparator();
        toolBar.add(tokenizeButton);
        toolBar.add(syntaxButton);

        add(toolBar, BorderLayout.NORTH);
    }

    private void createEditorPane() {
        codeEditor = new JTextPane();
        document = codeEditor.getStyledDocument();
        codeEditor.setFont(editorFont);
        codeEditor.setBackground(currentBackground);
        codeEditor.setForeground(currentForeground);
        codeEditor.setCaretColor(currentForeground);
        codeEditor.setSelectionColor(currentSelectionColor);

        // Set up document styles
        // addStylesToDocument();

        // Document listener for syntax highlighting and status updates
        codeEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateStatus();
                SwingUtilities.invokeLater(() -> highlightSyntax());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStatus();
                SwingUtilities.invokeLater(() -> highlightSyntax());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStatus();
                SwingUtilities.invokeLater(() -> highlightSyntax());
            }
        });

        // Add key bindings for indentation
        InputMap inputMap = codeEditor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = codeEditor.getActionMap();

        // Tab key for indentation
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "insert-tab");
        actionMap.put("insert-tab", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    document.insertString(codeEditor.getCaretPosition(), "    ", null);
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
                updateStatus();
            }
        });

        // Auto-indentation on enter key
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "insert-break-and-indent");
        actionMap.put("insert-break-and-indent", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int caretPos = codeEditor.getCaretPosition();
                String text = codeEditor.getText();

                // Get current line indentation
                int lineStart = 0;
                for (int i = caretPos - 1; i >= 0; i--) {
                    if (text.charAt(i) == '\n') {
                        lineStart = i + 1;
                        break;
                    }
                }

                StringBuilder indentation = new StringBuilder();
                for (int i = lineStart; i < text.length() && i < caretPos; i++) {
                    if (text.charAt(i) == ' ' || text.charAt(i) == '\t') {
                        indentation.append(text.charAt(i));
                    } else {
                        break;
                    }
                }

                // Check if we need additional indentation (e.g., after '{')
                boolean needsExtraIndent = false;
                for (int i = lineStart; i < caretPos; i++) {
                    if (text.charAt(i) == '{') {
                        needsExtraIndent = true;
                        break;
                    }
                }
                try {
                    document.insertString(caretPos, "\n" + indentation + (needsExtraIndent ? "    " : ""), null);
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
                updateStatus();
            }
        });
    }

    private void addStylesToDocument() {
        Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        Style keywordStyle = document.addStyle("keyword", defaultStyle);
        StyleConstants.setForeground(keywordStyle, keywordColor);
        StyleConstants.setBold(keywordStyle, true);

        Style commentStyle = document.addStyle("comment", defaultStyle);
        StyleConstants.setForeground(commentStyle, commentColor);
        StyleConstants.setItalic(commentStyle, true);

        Style stringStyle = document.addStyle("string", defaultStyle);
        StyleConstants.setForeground(stringStyle, stringColor);

        Style numberStyle = document.addStyle("number", defaultStyle);
        StyleConstants.setForeground(numberStyle, numberColor);
    }

    private void highlightSyntax() {
        // String content = codeEditor.getText();
        // document.setCharacterAttributes(0, content.length(),
        // document.getStyle(StyleContext.DEFAULT_STYLE), true);

        // // Combine all keywords into a single regex pattern
        // String keywordPatternStr = "\\b(" + String.join("|", keywords) + ")\\b";
        // Pattern keywordPattern = Pattern.compile(keywordPatternStr);
        // applyStyle(keywordPattern, "keyword");

        // // Rest of your highlighting patterns...
        // applyStyle(Pattern.compile("//.*$", Pattern.MULTILINE), "comment");
        // applyStyle(Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL), "comment");
        // applyStyle(Pattern.compile("\".*?\""), "string");
        // applyStyle(Pattern.compile("\\b\\d+\\b"), "number");
    }

    private void applyStyle(Pattern pattern, String styleName) {
        String text = codeEditor.getText();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            document.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(),
                    document.getStyle(styleName), true);
        }
    }

    private void createStatusBar() {
        statusBar = new StatusBar();
        updateStatus();
    }

    private void updateStatus() {
        String text = codeEditor.getText();
        int chars = text.length();
        int lines = text.isEmpty() ? 1 : text.split("\n", -1).length;

        // Count words
        StringTokenizer tokenizer = new StringTokenizer(text);
        int words = tokenizer.countTokens();

        // Get current caret position
        int caretPos = codeEditor.getCaretPosition();
        int currentLine = 1;
        int currentCol = 1;

        try {
            int lineStartOffset = 0;
            for (int i = 0; i < caretPos; i++) {
                if (text.charAt(i) == '\n') {
                    lineStartOffset = i + 1;
                    currentLine++;
                    currentCol = 1;
                }
            }
            currentCol = caretPos - lineStartOffset + 1;
        } catch (Exception e) {
            // Handle any potential exceptions when calculating line/column
        }

        // Update status bar
        statusBar.setWordCount(words);
        statusBar.setCharCount(chars);
        statusBar.setLineCount(lines);

        // Update title if file has changed
        if (currentFilePath != null) {
            if (isFileModified()) {
                if (!getTitle().endsWith("*")) {
                    setTitle("Java Code Compiler - " + new File(currentFilePath).getName() + "*");
                }
            } else {
                setTitle("Java Code Compiler - " + new File(currentFilePath).getName());
            }
        }
    }

    private boolean isFileModified() {
        if (currentFilePath == null) {
            return !codeEditor.getText().isEmpty();
        }

        try {
            String fileContent = readFile(currentFilePath);
            return !fileContent.equals(codeEditor.getText());
        } catch (IOException e) {
            return true;
        }
    }

    private String readFile(String path) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }

    private void tokenizeCode() {
        tokenCount.clear();
        symbols.clear();

        String codeText = codeEditor.getText();
        String[] lines = codeText.split("\\r?\\n");
        boolean inBlockComment = false;

        // First check for syntax errors
        ArrayList<SyntaxError> errors = checkSyntaxErrors();
        
        // If there are syntax errors, show them in a popup
        if (!errors.isEmpty()) {
            // Create a styled text pane for the error message
            JTextPane errorPane = new JTextPane();
            StyledDocument doc = errorPane.getStyledDocument();
            
            // Create a style for error messages
            Style errorStyle = errorPane.addStyle("Error", null);
            StyleConstants.setForeground(errorStyle, Color.RED);
            StyleConstants.setBold(errorStyle, true);
            
            // Create a style for the header
            Style headerStyle = errorPane.addStyle("Header", null);
            StyleConstants.setForeground(headerStyle, Color.BLACK);
            StyleConstants.setBold(headerStyle, true);
            StyleConstants.setFontSize(headerStyle, 14);
            
            try {
                // Add header
                doc.insertString(doc.getLength(), "Syntax Errors Detected:\n\n", headerStyle);
                
                // Add each error with line number
                for (SyntaxError error : errors) {
                    doc.insertString(doc.getLength(), "Line " + error.getLineNumber() + ": ", null);
                    doc.insertString(doc.getLength(), error.getMessage() + "\n", errorStyle);
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            
            // Set the size of the error pane
            errorPane.setPreferredSize(new Dimension(400, 200));
            
            // Show the error pane in a JOptionPane
            JScrollPane scrollPane = new JScrollPane(errorPane);
            JOptionPane.showMessageDialog(this, scrollPane, "Syntax Errors", JOptionPane.ERROR_MESSAGE);
            return;
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            StringBuilder cleanedLine = new StringBuilder();
            int index = 0;

            while (index < line.length()) {
                if (inBlockComment) {
                    int endBlock = line.indexOf("*/", index);
                    if (endBlock != -1) {
                        index = endBlock + 2;
                        inBlockComment = false;
                    } else {
                        index = line.length();
                    }
                } else {
                    int lineComment = line.indexOf("//", index);
                    int blockComment = line.indexOf("/*", index);

                    if (lineComment != -1 && (blockComment == -1 || lineComment < blockComment)) {
                        cleanedLine.append(line, index, lineComment);
                        index = line.length();
                    } else if (blockComment != -1) {
                        cleanedLine.append(line, index, blockComment);
                        inBlockComment = true;
                        index = blockComment + 2;
                    } else {
                        cleanedLine.append(line.substring(index));
                        index = line.length();
                    }
                }
            }

            String processedLine = cleanedLine.toString().trim();

            if (processedLine.isEmpty())
                continue;

            // Handle multi-line declarations and initializations
            if (processedLine.matches("^(int|double|String|char|boolean|float|long)\\b.*")) {
                StringBuilder declaration = new StringBuilder(processedLine);
                int startLine = i + 1;
                boolean hasSemicolon = processedLine.contains(";");
                while (!hasSemicolon && i < lines.length - 1) {
                    i++;
                    line = lines[i];
                    cleanedLine = new StringBuilder();
                    index = 0;
                    while (index < line.length()) {
                        if (inBlockComment) {
                            int endBlock = line.indexOf("*/", index);
                            if (endBlock != -1) {
                                index = endBlock + 2;
                                inBlockComment = false;
                            } else {
                                index = line.length();
                            }
                        } else {
                            int lineComment = line.indexOf("//", index);
                            int blockComment = line.indexOf("/*", index);

                            if (lineComment != -1 && (blockComment == -1 || lineComment < blockComment)) {
                                cleanedLine.append(line, index, lineComment);
                                index = line.length();
                            } else if (blockComment != -1) {
                                cleanedLine.append(line, index, blockComment);
                                inBlockComment = true;
                                index = blockComment + 2;
                            } else {
                                cleanedLine.append(line.substring(index));
                                index = line.length();
                            }
                        }
                    }
                    String cleaned = cleanedLine.toString().trim();
                    declaration.append(" ").append(cleaned);
                    if (cleaned.contains(";")) {
                        hasSemicolon = true;
                        break;
                    }
                }
                processedLine = declaration.toString().replaceAll(";.*", ";");
            }

            processLine(processedLine, i + 1);
        }

        updateSymbolTable();
        updateTokenTable();
        JOptionPane
                .showMessageDialog(this,
                        "Tokenization completed successfully!\nFound " + tokenCount.size() + " unique tokens and "
                                + symbols.size() + " symbols.",
                        "Tokenization Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void processLine(String line, int lineNum) {
        // Process different statement types
        if (isDeclarationStatement(line)) {
            processDeclaration(line, lineNum);
        } else if (isInitializationStatement(line)) {
            processInitialization(line, lineNum);
        } else if (isAssignmentStatement(line)) {
            processAssignment(line, lineNum);
        } else if (isIfStatement(line)) {
            processIfStatement(line, lineNum);
        } else if (isForLoop(line)) {
            processForLoop(line, lineNum);
        } else {
            // Process other statements for tokenization
            tokenizeLine(line, lineNum);
        }
    }

    private void styleTable(JTable table) {
        table.setShowGrid(true);
        table.setGridColor(new Color(200, 200, 200));
        table.setRowHeight(22);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setSelectionBackground(new Color(173, 214, 255));
    }

    private boolean isDeclarationStatement(String line) {
        return line.matches("\\s*(int|double|String|char|boolean|float|long)\\s+" +
                "[a-zA-Z_][a-zA-Z0-9_]*(\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_]*)*\\s*;.*");
    }

    private boolean isInitializationStatement(String line) {
        return line.matches("\\s*(int|double|String|char|boolean|float|long)\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*=.*;.*");
    }

    private boolean isAssignmentStatement(String line) {
        return line.matches(".*\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*=.*;.*") && !isInitializationStatement(line);
    }

    private boolean isIfStatement(String line) {
        // Check for if statements
        return line.trim().startsWith("if") || line.trim().startsWith("} else if") || line.trim().startsWith("else if");
    }

    private boolean isForLoop(String line) {
        // Check for for loops
        return line.trim().startsWith("for");
    }

    private int calculateSize(String type, String value) {
        switch (type) {
            case "int":
                return 2;
            case "char":
                return value != null ? value.length() + 1 : 1; // +1 for null terminator
            // Add other types as needed
            default:
                return 0;
        }
    }

    private int getDimension(String declaration) {
        return declaration.contains("[]") ? 1 : 0;
    }

    private void processDeclaration(String line, int lineNum) {
        Pattern pattern = Pattern.compile(
                "\\b(int|double|String|char|boolean|float|long)(\\s*\\[\\s*\\])*\\s+" +
                        "([a-zA-Z_][a-zA-Z0-9_]*\\s*(\\[\\s*\\])*(\\s*,\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*(\\[\\s*\\])*)*)\\s*;");
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.find()) {
            String type = matcher.group(1) + (matcher.group(2) != null ? matcher.group(2).replaceAll("\\s", "") : "");
            String varsList = matcher.group(3).replaceAll("\\s*\\[\\s*\\]", "[]"); // Normalize array syntax
            String[] varNames = varsList.split("\\s*,\\s*");

            addToken(type.replaceAll("\\s", ""), "keyword", lineNum);
            for (String var : varNames) {
                var = var.trim();
                if (var.isEmpty())
                    continue;
                boolean isArray = var.contains("[]");
                String name = var.replaceAll("\\[\\]", "");
                int dimension = var.split("\\[\\]").length - 1;
                symbols.put(name,
                        new SymbolInfo(name, type, calculateSize(type, null), dimension, lineNum, "--", "--"));
                addToken(name, "identifier", lineNum);
            }
            addToken(";", "separator", lineNum);
        }
    }

    private void processInitialization(String line, int lineNum) {
        // Modified regex to handle array and non-array initializations for all types
        Pattern pattern = Pattern.compile(
                "\\b(int|double|String|char|boolean|float|long)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(\\s*\\[\\s*\\])?\\s*=\\s*(.*?)\\s*;");
        Matcher matcher = pattern.matcher(line.trim());

        if (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            boolean isArray = matcher.group(3) != null;
            String value = matcher.group(4);

            int dimension = isArray ? 1 : 0;
            int size = calculateSize(type, value);

            symbols.put(name, new SymbolInfo(
                    name,
                    type + (isArray ? "[]" : ""),
                    size,
                    dimension,
                    lineNum,
                    "--",
                    "--"));
            addToken(name, "identifier", lineNum);
            addToken("=", "operator", lineNum);
            addToken(value, "literal", lineNum);
            addToken(";", "separator", lineNum);
        }
    }

    private void processAssignment(String line, int lineNum) {
        // Extract variable name and value from assignment
        Pattern pattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*([^;]+)\\s*;");
        Matcher matcher = pattern.matcher(line.trim());

        if (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);

            // Update symbol table if variable exists
            if (symbols.containsKey(name)) {
                SymbolInfo info = symbols.get(name);
            }

            // Count tokens
            addToken(name, "identifier", lineNum);
            addToken("=", "operator", lineNum);
            addToken(value, "literal", lineNum);
            addToken(";", "separator", lineNum);
        }

        // Tokenize the whole line as well
        tokenizeLine(line, lineNum);
    }

private void processIfStatement(String line, int lineNum) {
    // For if statements, we'll extract condition and tokenize
    if (line.trim().startsWith("if") || line.trim().startsWith("} else if") || line.trim().startsWith("else if")) {
        // Extract the condition between $(...)$
        Pattern pattern = Pattern.compile("\\$\\(([^)]+)\\)\\$");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String condition = matcher.group(1);

            // Count tokens
            addToken("if", "keyword", lineNum);
            addToken("(", "separator", lineNum);

            // Tokenize the condition
            StringTokenizer tokenizer = new StringTokenizer(condition, " \t\n\r\f+-*/=<>!&|^%.", true);
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken().trim();
                if (!token.isEmpty()) {
                    if (isOperator(token)) {
                        addToken(token, "operator", lineNum);
                    } else if (isKeyword(token)) {
                        addToken(token, "keyword", lineNum);
                    } else if (isIdentifier(token)) {
                        addToken(token, "identifier", lineNum);
                    } else {
                        addToken(token, "literal", lineNum);
                    }
                }
            }

            addToken(")", "separator", lineNum);
        }

        if (line.contains("else")) {
            addToken("else", "keyword", lineNum);
        }
    } else if (line.trim().startsWith("else")) {
        addToken("else", "keyword", lineNum);
    }

    // Tokenize the whole line as well
    tokenizeLine(line, lineNum);
}

private void processForLoop(String line, int lineNum) {
    if (line.trim().startsWith("for")) {
        // Extract the three parts of the for loop from $(init; condition; increment)$
        Pattern pattern = Pattern.compile("for\\s*\\$\\(([^;]+);([^;]+);([^)]+)\\)\\$");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String initialization = matcher.group(1);
            String condition = matcher.group(2);
            String increment = matcher.group(3);

            // Count tokens
            addToken("for", "keyword", lineNum);
            addToken("(", "separator", lineNum);

            // Tokenize the initialization part
            tokenizePart(initialization, lineNum);
            addToken(";", "separator", lineNum);

            // Tokenize the condition part
            tokenizePart(condition, lineNum);
            addToken(";", "separator", lineNum);

            // Tokenize the increment part
            tokenizePart(increment, lineNum);

            addToken(")", "separator", lineNum);
        }
    }

    // Tokenize the whole line as well
    tokenizeLine(line, lineNum);
}


    private void tokenizePart(String part, int lineNum) {
        StringTokenizer tokenizer = new StringTokenizer(part, " \t\n\r\f+-*/=<>!&|^%.", true);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (!token.isEmpty()) {
                if (isOperator(token)) {
                    addToken(token, "operator", lineNum);
                } else if (isKeyword(token)) {
                    addToken(token, "keyword", lineNum);
                } else if (isIdentifier(token)) {
                    addToken(token, "identifier", lineNum);
                } else {
                    addToken(token, "literal", lineNum);
                }
            }
        }
    }

    private void tokenizeLine(String line, int lineNum) {
        // Corrected regex pattern with properly escaped [ and ]
        Pattern pattern = Pattern
                .compile("\"(.*?)\"|([a-zA-Z_]\\w*)|(\\d+\\.?\\d*)|([+\\-*/=<>!&|^%.,;(){}\\\\[\\\\]])|(\\S+)");
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null)
                continue;

            if (token.startsWith("\"") && token.endsWith("\"")) {
                addToken(token, "string literal", lineNum);
            } else if (keywords.contains(token)) {
                addToken(token, "keyword", lineNum);
            } else if (isOperator(token)) {
                addToken(token, "operator", lineNum);
            } else if (isSeparator(token)) {
                addToken(token, "separator", lineNum);
            } else if (token.matches("[a-zA-Z_]\\w*")) {
                addToken(token, "identifier", lineNum);
            } else if (token.matches("\\d+")) {
                addToken(token, "integer literal", lineNum);
            } else if (token.matches("\\d+\\.\\d+")) {
                addToken(token, "float literal", lineNum);
            } else {
                addToken(token, "unknown", lineNum);
            }
        }
    }

    private boolean isOperator(String token) {
        return "+-*/=<>!&|^%".contains(token);
    }

    private boolean isSeparator(String token) {
        return "()[]{};,.".contains(token);
    }

    private boolean isKeyword(String token) {
        return keywords.contains(token);
    }

    private boolean isIdentifier(String token) {
        return token.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    private void addToken(String token, String type, int lineNum) {
        String key = token + ":" + type;
        if (tokenCount.containsKey(key)) {
            TokenInfo info = tokenCount.get(key);
            info.incrementCount();
            info.addLineNumber(lineNum);
        } else {
            tokenCount.put(key, new TokenInfo(token, type, lineNum));
        }
    }

    private void updateSymbolTable() {
        String[] columns = { "Name", "Type", "Size", "Dimension", "Line of Declaration", "Line of Usage", "Address" };
        Object[][] data = new Object[symbols.size()][7];

        int i = 0;
        for (SymbolInfo info : symbols.values()) {
            data[i][0] = info.getName();
            data[i][1] = info.getType();
            data[i][2] = info.getSize();
            data[i][3] = info.getDimension();
            data[i][4] = info.getLineOfDeclaration();
            data[i][5] = info.getLineOfUsage();
            data[i][6] = info.getAddress();
            i++;
        }

        // Update the table model and refresh the UI
        DefaultTableModel model = new DefaultTableModel(data, columns);
        symbolTable.setModel(model);
        symbolTable.repaint();
    }

    private void updateTokenTable() {
        String[] columns = { "Token", "Type", "Count", "Line Numbers" };
        Object[][] data = new Object[tokenCount.size()][4];

        int i = 0;
        for (TokenInfo info : tokenCount.values()) {
            data[i][0] = info.token;
            data[i][1] = info.type;
            data[i][2] = info.count;
            data[i][3] = info.lineNumbers.toString();
            i++;
        }

        tokenTable.setModel(new DefaultTableModel(data, columns));
    }

    private void createNewFile() {
        codeEditor.setText("");
        setTitle("Java Code Compiler - New File");
        tokenCount.clear();
        symbols.clear();
        updateSymbolTable();
        updateTokenTable();
    }

    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open Java File");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();

                codeEditor.setText(content.toString());
                setTitle("Java Code Compiler - " + selectedFile.getName());

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error opening file: " + e.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Java File");

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(selectedFile));
                writer.write(codeEditor.getText());
                writer.close();

                setTitle("Java Code Compiler - " + selectedFile.getName());
                JOptionPane.showMessageDialog(this, "File saved successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);

                       } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "Java Code Compiler\nVersion 1.0\n\nA simple Java code compiler with tokenization and syntax analysis capabilities.\n",
                "About Java Code Compiler",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // Inner class for symbol information
    private class SymbolInfo {
        private String name;
        private String type;
        private int size;
        private int dimension;
        private int lineOfDeclaration;
        private String lineOfUsage;
        private String address;

        public SymbolInfo(String name, String type, int size, int dimension,
                int lineOfDeclaration, String lineOfUsage, String address) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.dimension = dimension;
            this.lineOfDeclaration = lineOfDeclaration;
            this.lineOfUsage = lineOfUsage;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public int getSize() {
            return size;
        }

        public int getDimension() {
            return dimension;
        }

        public int getLineOfDeclaration() {
            return lineOfDeclaration;
        }

        public String getLineOfUsage() {
            return lineOfUsage;
        }

        public String getAddress() {
            return address;
        }
    }

    // Inner class for line numbers component
    private class LineNumberComponent extends JPanel {
        private JTextPane textPane;

        public LineNumberComponent(JTextPane textPane) {
            this.textPane = textPane;
            setPreferredSize(new Dimension(40, 0));
            setBackground(new Color(240, 240, 240));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(isDarkTheme ? new Color(150, 150, 180) : Color.GRAY);
            int lineHeight = textPane.getFontMetrics(textPane.getFont()).getHeight();
            int start = textPane.viewToModel2D(getVisibleRect().getLocation());
            Element root = textPane.getDocument().getDefaultRootElement();
            int startLine = root.getElementIndex(start);
            int endLine = root.getElementIndex(start + getVisibleRect().height);

            g.setColor(Color.GRAY);
            for (int i = startLine; i <= endLine; i++) {
                String lineNumber = String.valueOf(i + 1);
                int y = (i - startLine) * lineHeight + lineHeight - 5;
                g.drawString(lineNumber, getWidth() - g.getFontMetrics().stringWidth(lineNumber) - 5, y);
            }
        }
    }

    // Inner class for status bar
    private class StatusBar extends JPanel {
        private JLabel wordCountLabel;
        private JLabel charCountLabel;
        private JLabel lineCountLabel;

        public StatusBar() {
            setLayout(new FlowLayout(FlowLayout.LEFT));
            setBorder(new BevelBorder(BevelBorder.LOWERED));

            wordCountLabel = new JLabel("Words: 0");
            charCountLabel = new JLabel("Characters: 0");
            lineCountLabel = new JLabel("Lines: 0");

            add(wordCountLabel);
            add(Box.createHorizontalStrut(15));
            add(charCountLabel);
            add(Box.createHorizontalStrut(15));
            add(lineCountLabel);
        }

        public void setWordCount(int count) {
            wordCountLabel.setText("Words: " + count);
        }

        public void setCharCount(int count) {
            charCountLabel.setText("Characters: " + count);
        }

        public void setLineCount(int count) {
            lineCountLabel.setText("Lines: " + count);
        }

        public void setBackground(Color bg) {
            super.setBackground(bg);
            for (Component comp : getComponents()) {
                comp.setBackground(bg);
            }
        }

        public void setForeground(Color fg) {
            super.setForeground(fg);
            for (Component comp : getComponents()) {
                if (comp instanceof JLabel) {
                    ((JLabel) comp).setForeground(fg);
                }
            }
        }
    }

    // Inner class for token information
    private class TokenInfo {
        private String token;
        private String type;
        private int count;
        private Set<Integer> lineNumbers;

        public TokenInfo(String token, String type, int lineNum) {
            this.token = token;
            this.type = type;
            this.count = 1;
            this.lineNumbers = new HashSet<>();
            this.lineNumbers.add(lineNum);
        }

        public void incrementCount() {
            this.count++;
        }

        public void addLineNumber(int lineNum) {
            this.lineNumbers.add(lineNum);
        }
    }

    // Declare tokenCount
    private Map<String, TokenInfo> tokenCount = new HashMap<>();

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new Main2());
    }
}