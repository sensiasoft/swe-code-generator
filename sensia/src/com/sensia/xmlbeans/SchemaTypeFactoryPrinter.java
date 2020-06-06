package com.sensia.xmlbeans;

import java.io.CharArrayWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.xmlbeans.SchemaType;


/**
 * Prints the java code for reading and writing schema types from/to XML
 */
public final class SchemaTypeFactoryPrinter extends AbstractCodePrinter
{
    public final static String FACTORY_IMPL_CLASS_NAME = "DefaultFactory";
    public final static String FACTORY_IMPL_SUBPACKAGE_NAME = "impl";
    public final static String FACTORY_CLASS_NAME = "Factory";
    
    CharArrayWriter _charBuffer = new CharArrayWriter(1024);
    Writer _fileWriter;
    String _packageName;
    List<SchemaType> processedTypes = new ArrayList<SchemaType>();
    List<String> usedJavaTypes = new ArrayList<String>();
    Map<String, String> bindingClasses = new LinkedHashMap<String, String>();
    boolean _impl;

    
    public SchemaTypeFactoryPrinter(Writer fileWriter, boolean impl)
    {
        _indent = 0;
        _fileWriter = fileWriter;
        _writer = _charBuffer;
        _impl = impl;
    }
    
    
    public void endClassAndClose() throws IOException
    {
        outdent();
        
        // write package and imports
        _writer = _fileWriter;
        if (_impl)
            emit("package " + _packageName + "." + FACTORY_IMPL_SUBPACKAGE_NAME + ";");
        else
            emit("package " + _packageName + ";");
        emit("");
        
        Collections.sort(usedJavaTypes);
        for (String javaType: usedJavaTypes)
        {
            // skip primitive types
            if (javaType.indexOf('.') < 0)
                continue;
            
            // skip array types
            if (javaType.contains("[]"))
                continue;
            
            // skip java.lang types
            if (javaType.startsWith("java.lang"))
                continue;
            
            emit("import " + javaType + ";");
        }
        
        if (_impl)
            emit("import " + _packageName + "." + FACTORY_CLASS_NAME + ";");
        
        emit("");
        emit("");
        if (_impl)
            emit("public class " + FACTORY_IMPL_CLASS_NAME + " implements " + FACTORY_CLASS_NAME);
        else
            emit("public interface " + FACTORY_CLASS_NAME);
        startBlock();
        printDependencyBindingsVars();
        
        if (_impl)
        {
            emit("");
            printConstructor(FACTORY_IMPL_CLASS_NAME);
        }
        
        _charBuffer.writeTo(_fileWriter);
        endBlock();
        
        _fileWriter.close();
    }
    

    public void startClass(String packageName) throws IOException
    {
        _packageName = packageName;
        indent();
    }
    
    
    void printStaticFields(String nsUri) throws IOException
    {
        //emit("public final static String NS_URI = \"" + nsUri + "\";");        
    }
    
    
    void printDependencyBindingsVars() throws IOException
    {
        if (bindingClasses.size() > 0)
            emit("");
                
        for (Entry<String, String> dep: bindingClasses.entrySet())
            emit(dep.getKey() + " " + dep.getValue() + " = new " + dep.getKey() + "();");
    }
    
    
    public void printGenTypeMethod(SchemaType sType) throws IOException
    {
        if (sType.isAbstract() || sType.hasStringEnumValues())
            return;
        
        // don't print global elements if they have the same name as a type
        if (sType.isDocumentType() && sType.getShortJavaName().endsWith("Element"))
            return;
                
        String javaTypeName = sType.getShortJavaName();
                
        emit("");
        emit("");
        
        if (_impl)
            emit("@Override");
        
        emit("public " + javaTypeName + " new" + javaTypeName + "()" + (_impl ? "" : ";"));
        
        if (_impl)
        {
            startBlock();
            emit("return new " + sType.getShortJavaImplName() + "();");
            endBlock();
            
            addUsedJavaType(sType.getFullJavaName());
        }
    }
    
    
    public void addUsedJavaType(String fullJavaType)
    {
        if (fullJavaType == null || fullJavaType.endsWith("XmlObject"))
            return;
        
        if (!usedJavaTypes.contains(fullJavaType))
            usedJavaTypes.add(fullJavaType);
    }
    
    
    public static String getFactoryFullClassName(String packageName, boolean impl)
    {
        if (impl)
            return packageName + "." + FACTORY_IMPL_SUBPACKAGE_NAME + "." + FACTORY_IMPL_CLASS_NAME;
        else
            return packageName + "." + FACTORY_CLASS_NAME;
    }
}
