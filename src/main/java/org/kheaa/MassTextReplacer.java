package org.kheaa;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDStream;

import javax.swing.*;

public class MassTextReplacer {

    private JTextField tf1,tf2;
    private String[] args;

    private String searchString, replaceString;

    MassTextReplacer(String[] args) {
        JLabel l1,l2;
        JButton b1,b2;

        this.args = args;
        JFrame f = new JFrame();
        l1 = new JLabel("Find:");
        l1.setBounds(25, 50, 100, 20);
        tf1 = new JTextField();
        tf1.setBounds(175, 50, 150, 20);
        l2 = new JLabel("Replace with:");
        l2.setBounds(25, 100, 125, 20);
        tf2 = new JTextField();
        tf2.setBounds(175, 100, 150, 20);
        b1 = new JButton("Replace");
        b1.setBounds(50, 200, 150, 30);
        b2 = new JButton("Cancel");
        b2.setBounds(200, 200, 150, 30);
        b1.addActionListener(e -> {
            searchString = tf1.getText();
            replaceString = tf2.getText();

            f.setVisible(false);

            searchFile();
        });
        b2.addActionListener(e -> displayMessage("No search terms provided.", "Info", true, -2));
        f.add(l1);
        f.add(l2);
        f.add(tf1);
        f.add(tf2);
        f.add(b1);
        f.add(b2);
        f.setSize(400, 300);
        f.setLayout(null);
        f.setVisible(true);
    }

    public static void main(String[] args) {
        new MassTextReplacer(args);
    }

    private void searchFile() {
        File inputDirectory = getInputFile(args);
        if (inputDirectory.exists() && inputDirectory.isDirectory()) {

            File[] directoryListing = inputDirectory.listFiles();
            if (directoryListing != null) {

                prepareOutputDir();

                for (File child : directoryListing) {
                    try (PDDocument document = PDDocument.load(child)){
                        PDDocument newDoc = replaceText(document, searchString, replaceString);

                        saveCloseCurrent(child.getName(), newDoc);
                    } catch (IOException ioe) {
                        displayMessage("IO Error: \n" + ioe.getMessage(), "Error", true, -1);
                    }
                }
            }
        } else {
            displayMessage("No directory provided or directory does not exist", "Error", true, -1);
        }
        displayMessage("Text replaced successfully.", "Info", true, 0);
    }

    public static PDDocument replaceText(PDDocument document, String searchString, String replacement) throws IOException {
        if (searchString.isEmpty() || replacement.isEmpty()) {
            return document;
        }
        PDPageTree pages = document.getDocumentCatalog().getPages();
        for (PDPage page : pages) {
            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List tokens = parser.getTokens();
            for (int j = 0; j < tokens.size(); j++) {
                Object next = tokens.get(j);
                if (next instanceof Operator) {
                    Operator op = (Operator) next;
                    //Tj and TJ are the two operators that display strings in a PDF
                    if (op.getName().equals("Tj")) {
                        // Tj takes one operator and that is the string to display so lets update that operator
                        COSString previous = (COSString) tokens.get(j - 1);
                        String string = previous.getString();
                        string = string.replaceFirst(searchString, replacement);
                        previous.setValue(string.getBytes());
                    } else if (op.getName().equals("TJ")) {
                        COSArray previous = (COSArray) tokens.get(j - 1);
                        for (int k = 0; k < previous.size(); k++) {
                            Object arrElement = previous.getObject(k);
                            if (arrElement instanceof COSString) {
                                COSString cosString = (COSString) arrElement;
                                String string = cosString.getString();
                                string = string.replaceAll(searchString, replacement);
                                cosString.setValue(string.getBytes());
                            }
                        }
                    }
                }
            }
            // now that the tokens are updated we will replace the page content stream.
            PDStream updatedStream = new PDStream(document);
            OutputStream out = updatedStream.createOutputStream();
            ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
            tokenWriter.writeTokens(tokens);
            page.setContents(updatedStream);
            out.close();
        }
        return document;
    }

    private static File getInputFile(String[] args) {
        File file = new File("");

        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new java.io.File("."));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        int chooserResult = fileChooser.showOpenDialog(null);

        if (chooserResult == JFileChooser.APPROVE_OPTION) {
            file = fileChooser.getSelectedFile();
            System.out.println("User approved. File: " + file.getName());
        }

        return file;
    }

    private static void prepareOutputDir() {
        File outputdir = new File("output");

        if(!outputdir.isDirectory() && !outputdir.exists()) {
            try {
                outputdir.mkdir();
            } catch (SecurityException se) {
                displayMessage(
                        "You do not have permission to create the output directory: " + se.getMessage(),
                        "Error", true, -1);
            }
        }

        for (File file : Objects.requireNonNull(outputdir.listFiles())){
            file.delete();
        }
    }

    private static void saveCloseCurrent(String filename, PDDocument outputDocument)
            throws IOException
    {
        // save to new output file
        if (filename != null)
        {
            // save document into file
            File f = new File("output/" + filename);
            if (f.exists())
            {
                displayMessage("File " + f + " already exists!", "Error", true, -1);
            }
            outputDocument.save(f);
            outputDocument.close();
        }
    }

    private static void displayMessage(String message) {
        displayMessage(message, "Message");
    }

    private static void displayMessage(String message, String title) {
        displayMessage(message, title, false, 0);
    }

    private static void displayMessage(String message, String title, boolean exit, int code) {
        JOptionPane.showMessageDialog(
                null,
                message ,
                title,
                JOptionPane.PLAIN_MESSAGE );
        if (exit) {
            System.exit(code);
        }
    }
}
