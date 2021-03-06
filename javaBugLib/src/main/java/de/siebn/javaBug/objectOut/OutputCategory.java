package de.siebn.javaBug.objectOut;

import de.siebn.javaBug.JavaBug;
import de.siebn.javaBug.util.XML;

import java.util.List;

/**
 * Created by Sieben on 16.03.2015.
 */
public interface OutputCategory extends JavaBug.BugPlugin {
    public void add(XML ul, Object o);
    public String getType();
    public String getName(Object o);
    public boolean canOutputClass(Class<?> clazz);
    public boolean opened(List<OutputCategory> others, boolean alreadyOpened);
}
