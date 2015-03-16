package de.siebn.javaBug.testApplication;

import de.siebn.javaBug.*;
import de.siebn.javaBug.objectOut.FieldsOutput;
import de.siebn.javaBug.objectOut.MethodsOutput;
import de.siebn.javaBug.objectOut.StringOutput;
import de.siebn.javaBug.plugins.ClassPathBugPlugin;
import de.siebn.javaBug.plugins.ObjectBugPlugin;
import de.siebn.javaBug.plugins.RootBugPlugin;
import de.siebn.javaBug.plugins.ThreadsBugPlugin;

/**
 * Created by Sieben on 16.03.2015.
 */
public class JavaBugDemoApplication {

    public static void main(String[] args) {
        JavaBug jb = new JavaBug(7777);
        jb.addPlugin(new RootBugPlugin(jb));
        jb.addPlugin(new ThreadsBugPlugin(jb));
        jb.addPlugin(new ClassPathBugPlugin());
        jb.addPlugin(jb.getObjectBug());

        jb.addPlugin(new FieldsOutput(jb));
        jb.addPlugin(new MethodsOutput(jb));
        jb.addPlugin(new StringOutput(jb));

        jb.getObjectBug().addRootObject(new TestClass());
        jb.getObjectBug().addRootObject(jb);

        jb.tryToStart();

        System.out.println("javaBug startet. Open your browser at: " + jb.getIPAddresses(true));

        while(true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
            }
        }
    }
}