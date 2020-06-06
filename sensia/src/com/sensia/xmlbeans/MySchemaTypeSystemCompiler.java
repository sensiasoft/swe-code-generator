/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sensia.xmlbeans;

import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.Filer;
import org.apache.xmlbeans.SchemaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;
import org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl;
import com.sensia.xmlbeans.SchemaTypeFactoryPrinter;
import com.sensia.xmlbeans.SchemaTypeReadWriteXMLPrinter;
import java.util.Iterator;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import javax.xml.namespace.QName;


public class MySchemaTypeSystemCompiler
{
    
    public static boolean generateTypes(SchemaTypeSystem system, Filer filer, XmlOptions options)
    {
        // partial type systems not allowed to be saved
        if (system instanceof SchemaTypeSystemImpl && ((SchemaTypeSystemImpl)system).isIncomplete())
            return false;
        
        boolean success = true;

        List<SchemaType> types = new ArrayList<>();
        types.addAll(Arrays.asList(system.globalTypes()));
        types.addAll(Arrays.asList(system.documentTypes()));
        types.addAll(Arrays.asList(system.attributeTypes()));
        
        // map of package to code writer instances
        // these will hold persistent writers that are used for several objects
        Map<String, SchemaTypeFactoryPrinter> nsToFactoryPrinter = new HashMap<>();
        Map<String, SchemaTypeFactoryPrinter> nsToFactoryImplPrinter = new HashMap<>();
        Map<String, SchemaTypeJsonSchemaPrinter> nsToJsonSchemaPrinter = new HashMap<>();
        Map<String, SchemaTypeReadWriteXMLPrinter> nsToXmlReadWritePrinter = new HashMap<>();
        Map<String, SchemaTypeReadWriteJsonPrinter> nsToJsonReadWritePrinter = new HashMap<>();
                
        for (Iterator<SchemaType> i = types.iterator(); i.hasNext(); )
        {
            SchemaType type = (SchemaType)i.next();
            String fjn = type.getFullJavaName();
            
            if (type.isBuiltinType())
                continue;
            if (fjn == null)
                continue;
            if (isOnClassPath(type))
                continue;
            if (!isGenerated(type))
                continue;
            
            Writer writer = null;
            int lastDot = type.getFullJavaName().lastIndexOf('.');
            String packageName = type.getFullJavaName().substring(0, lastDot);
            
            if (!(type.isDocumentType() && type.isAbstract()))
            {
                SchemaTypeCodePrinter codePrinter = new SchemaTypeCodePrinter();
                
                // Generate interface class
                try
                {
                    writer = filer.createSourceFile(fjn);
                    codePrinter.printType(writer, type);
                }
                catch (IOException e)
                {
                    System.err.println("IO Error " + e);
                    success = false;
                }
                finally {
                    try { if (writer != null) writer.close(); } catch (IOException e) {}
                }
    
                // Generate implementation class
                if (!type.isSimpleType())
                {
                    try
                    {                    
                        fjn = type.getFullJavaImplName();
                        writer = filer.createSourceFile(fjn);
                        codePrinter.printTypeImpl(writer, type);
                    }
                    catch (IOException e)
                    {
                        System.err.println("IO Error " + e);
                        success = false;
                    }
                    finally {
                        try { if (writer != null) writer.close(); } catch (IOException e) {}
                    }
                }
            }
            
            // Generate factory interface
            try
            {
                // Create or reuse writer for this package
                SchemaTypeFactoryPrinter codePrinter = nsToFactoryPrinter.get(packageName);
                if (codePrinter == null) {
                    fjn = SchemaTypeFactoryPrinter.getFactoryFullClassName(packageName, false);
                    writer = filer.createSourceFile(fjn);
                    codePrinter = new SchemaTypeFactoryPrinter(writer, false);
                    nsToFactoryPrinter.put(packageName, codePrinter);
                    codePrinter.startClass(packageName);
                }
                
                // Generate gen method
                codePrinter.printGenTypeMethod(type);
            }
            catch (IOException e)
            {
                System.err.println("IO Error " + e);
                success = false;
            }
            
            // Generate default factory impl
            try
            {
                // Create or reuse writer for this package
                SchemaTypeFactoryPrinter codePrinter = nsToFactoryImplPrinter.get(packageName);
                if (codePrinter == null) {
                    fjn = SchemaTypeFactoryPrinter.getFactoryFullClassName(packageName, true);
                    writer = filer.createSourceFile(fjn);
                    codePrinter = new SchemaTypeFactoryPrinter(writer, true);
                    nsToFactoryImplPrinter.put(packageName, codePrinter);
                    codePrinter.startClass(packageName);
                }
                
                // Generate writer class
                codePrinter.printGenTypeMethod(type);
            }
            catch (IOException e)
            {
                System.err.println("IO Error " + e);
                success = false;
            }
            
            // Generate JSON schema
            try
            {
                // Create or reuse writer for this package
            	SchemaTypeJsonSchemaPrinter codePrinter = nsToJsonSchemaPrinter.get(packageName);
                if (codePrinter == null) {
                    fjn = SchemaTypeJsonSchemaPrinter.getSchemaFileName(packageName);
                    //writer = filer.createSourceFile(fjn);
                    writer = new FileWriter(packageName + ".json");
                    codePrinter = new SchemaTypeJsonSchemaPrinter(writer, true);
                    nsToJsonSchemaPrinter.put(packageName, codePrinter);
                    codePrinter.startClass(packageName);
                }
                
                // Generate writer class
                codePrinter.printTypeDef(type);
            }
            catch (IOException e)
            {
                System.err.println("IO Error " + e);
                success = false;
            }
            
            // Generate XML read/write methods for elements and types
            try
            {
                // Create or reuse writer for this package
                SchemaTypeReadWriteXMLPrinter codePrinter = nsToXmlReadWritePrinter.get(packageName);
                if (codePrinter == null) {
                    fjn = SchemaTypeReadWriteXMLPrinter.getBindingsFullClassName(packageName);
                    writer = filer.createSourceFile(fjn);
                    codePrinter = new SchemaTypeReadWriteXMLPrinter(writer);
                    nsToXmlReadWritePrinter.put(packageName, codePrinter);
                   
                    QName qname = type.getName();
                    if (qname == null)
                        qname = type.getDocumentElementName();
                    String nsUri = qname.getNamespaceURI();
                    codePrinter.startClass(packageName, nsUri);
                }
                
                // Generate writer class
                codePrinter.printReadWriteMethods(type);
            }
            catch (IOException e)
            {
                System.err.println("IO Error " + e);
                success = false;
            }
            
            // Generate JSON read/write methods for elements and types
            try
            {
                // Create or reuse writer for this package
                SchemaTypeReadWriteJsonPrinter codePrinter = nsToJsonReadWritePrinter.get(packageName);
                if (codePrinter == null) {
                    fjn = SchemaTypeReadWriteJsonPrinter.getBindingsFullClassName(packageName);
                    writer = filer.createSourceFile(fjn);
                    codePrinter = new SchemaTypeReadWriteJsonPrinter(writer);
                    nsToJsonReadWritePrinter.put(packageName, codePrinter);
                   
                    QName qname = type.getName();
                    if (qname == null)
                        qname = type.getDocumentElementName();
                    String nsUri = qname.getNamespaceURI();
                    codePrinter.startClass(packageName, nsUri);
                }
                
                // Generate writer class
                codePrinter.printReadWriteMethods(type);
            }
            catch (IOException e)
            {
                System.err.println("IO Error " + e);
                success = false;
            }
        }
        
        // close all persistent writers
        for (String packageName: nsToXmlReadWritePrinter.keySet()) {
            try { 
                nsToFactoryPrinter.get(packageName).endClassAndClose();
                nsToFactoryImplPrinter.get(packageName).endClassAndClose();
                nsToJsonSchemaPrinter.get(packageName).endClassAndClose();
                nsToXmlReadWritePrinter.get(packageName).endClassAndClose();
                nsToJsonReadWritePrinter.get(packageName).endClassAndClose();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return success;
    }
    
    
    public static boolean isOnClassPath(SchemaType sType)
    {
        //if (sType.isDocumentType())
        //    sType = sType.getContentModel().getType();
        
        String className = sType.getFullJavaName();
        if (className.equals(Object.class.getCanonicalName()))
            return true;
        
        if (className.startsWith("net.opengis.sensorml"))
            return false;
        
        if (className.startsWith("net.opengis.fes"))
            return false;
        
        if (className.endsWith("Document"))
            className = className.substring(0, className.length()-8);
        try
        {
            Class.forName(className);
            System.out.println("Classpath includes " + className);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }
    
    
    public static boolean isGenerated(SchemaType sType)
    {
        // skip xlink stuff
        QName qname = sType.getName();
        if (qname == null)
            qname = sType.getDocumentElementName();
        if (qname != null && qname.getNamespaceURI().equals("http://www.w3.org/1999/xlink"))
            return false;
        
        // if document type, check content type
        if (sType.isDocumentType())
        {
            sType = sType.getContentModel().getType();
            if (sType == null)
                return false;
        }
        
        String javaTypeName = sType.getShortJavaName();
        if (javaTypeName.endsWith("Property") || javaTypeName.endsWith("PropertyByValue"))
        {
            if (sType.getContentType() == SchemaType.SIMPLE_CONTENT || sType.getContentType() == SchemaType.MIXED_CONTENT)
                return true;
            
            if (sType.isURType())
                return true;
            
            // in case object name itself ends with "Property", check if we have a corresponding type ending with PropertyPropertyType
            boolean hasProp = sType.getTypeSystem().findType(new QName(sType.getName().getNamespaceURI(), javaTypeName + "PropertyType")) != null;
            if (!hasProp)
                return false;
        }
        
        // always process top level elements except if they are properties
        if (sType.isDocumentType())
            return true;
        
        if (sType.isSimpleType() && !sType.hasStringEnumValues())
            return false;
        
        if (sType.isURType() || sType.isAnonymousType())
            return false;
        
        return true;
    }
}
