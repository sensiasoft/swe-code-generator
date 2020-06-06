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
import javax.xml.namespace.QName;
import org.apache.xmlbeans.impl.common.NameUtil;
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaProperty;


/**
 * Prints the java code for reading and writing schema types from/to XML
 */
public final class SchemaTypeReadWriteXMLPrinter extends AbstractCodePrinter
{
    public final static String BINDING_CLASS_NAME = "XMLStreamBindings";
    public final static String BINDING_SUBPACKAGE_NAME = "bind";
    
    CharArrayWriter _charBuffer = new CharArrayWriter(1024);
    Writer _fileWriter;
    String _packageName;
    String _nsUri;
    List<SchemaType> processedTypes = new ArrayList<SchemaType>();    
    Map<String, String> bindingClasses = new LinkedHashMap<String, String>();
    

    public SchemaTypeReadWriteXMLPrinter(Writer fileWriter)
    {
        _indent = 0;
        _fileWriter = fileWriter;
        _writer = _charBuffer;
    }
    
    
    public void endClassAndClose() throws IOException
    {
        outdent();
        
        // write package and imports
        _writer = _fileWriter;
        emit("package " + _packageName + "." + BINDING_SUBPACKAGE_NAME + ";");
        
        emit("");
        emit("import java.util.Map;");
        emit("import javax.xml.stream.XMLStreamConstants;");
        emit("import javax.xml.stream.XMLStreamException;");
        emit("import javax.xml.stream.XMLStreamReader;");
        emit("import javax.xml.stream.XMLStreamWriter;");
        emit("import net.opengis.AbstractXMLStreamBindings;");
        
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
            
            if (javaType.endsWith("." + OGC_PROP_IFACE_NAME))
                emit("import " + OGC_PROP_PACKAGE_NAME + OGC_PROP_CLASS_NAME + ";");
        }
        emit("import " + _packageName + ".Factory;");
        
        emit("");
        emit("");
        emit("public class " + BINDING_CLASS_NAME + " extends AbstractXMLStreamBindings");
        startBlock();
        printStaticFields(_nsUri);
        printDependencyBindingsVars();
        
        // print factory var
        emit("Factory factory;");
        
        // constructor
        printConstructor(BINDING_CLASS_NAME);
        
        _charBuffer.writeTo(_fileWriter);
        endBlock();
        
        _fileWriter.close();
    }
    

    public void startClass(String packageName, String nsUri) throws IOException
    {
        _packageName = packageName;
        _nsUri = nsUri;
        indent();
    }
    
    
    void printStaticFields(String nsUri) throws IOException
    {
        emit("public final static String NS_URI = \"" + nsUri + "\";");        
    }
    
    
    void printDependencyBindingsVars() throws IOException
    {
        if (bindingClasses.size() > 0)
            emit("");
                
        for (Entry<String, String> dep: bindingClasses.entrySet())
            emit(dep.getKey() + " " + dep.getValue() + ";");
    }
    
    
    String getBindingsVarName(SchemaType sType)
    {
        if (sType.getShortJavaName().equals("Object") || sType.getFullJavaName().startsWith(_packageName))
            return "this";
        
        String packageName = getJavaPackage(sType, false);
        String bindingClass = getBindingsFullClassName(packageName);
        String varName = bindingClasses.get(bindingClass);
        
        if (varName == null)
        {
            //TODO get better var name?
            varName = "ns" + (bindingClasses.size()+1) + "Bindings";
            bindingClasses.put(bindingClass, varName);
        }
        
        return varName;
    }
    
    
    @Override
    void printConstructor(String className) throws IOException
    {
        emit("");
        emit("");
        
        // generate factory arguments
        int i = 1;
        String factoryArgs = "";
        for (Entry<String, String> dep: bindingClasses.entrySet())
        {
            factoryArgs += ", " + dep.getKey().replace(BINDING_SUBPACKAGE_NAME + "." + BINDING_CLASS_NAME, "Factory");
            factoryArgs += " ns" + i + "Factory";
            i++;
        }
        
        emit("public " + className + "(Factory factory" + factoryArgs + ")");
        startBlock();
        emit("this.factory = factory;");
        i = 1;
        for (Entry<String, String> dep: bindingClasses.entrySet())
            emit(dep.getValue() + " = new " + dep.getKey() + "(ns" + i++ + "Factory);");
        endBlock();
    }
    
    
    public void printReadWriteMethods(SchemaType sType) throws IOException
    {
        if (sType.isDocumentType())
            printReadWriteElementMethods(sType);
        else
            printReadWriteTypeMethods(sType);
    }
    
    
    void printReadWriteElementMethods(SchemaType eltType) throws IOException
    {
        SchemaType contentType = eltType.getContentModel().getType();
        if (contentType == null)
            return;
        
        addUsedJavaType(contentType.getFullJavaName());
        //if (!contentType.isAbstract())
        //    addUsedJavaType(contentType.getFullJavaImplName());
        
        // also print read/write methods for this element complex type if in same package
        // this will only be done if complex type hasn't been processed yet
        String eltNs = getJavaPackage(eltType, false);
        String typeNs = getJavaPackage(contentType, false);
        if (contentType.isAnonymousType() && !contentType.isSimpleType() && eltNs.equals(typeNs))
            printReadWriteTypeMethods(contentType);
        
        // print read/write element method
        if (eltType.isAbstract() || contentType.isAbstract())
        {
            printReadMethodAbstract(eltType, contentType);
            printWriteMethodAbstract(eltType, contentType);
        }
        else
        {
            printReadMethodConcrete(eltType, contentType);
            printWriteMethodConcrete(eltType, contentType);
        }
    }
    
    
    void printReadWriteTypeMethods(SchemaType contentType) throws IOException
    {
        // no need for read/write methods for string enums
        if (contentType.hasStringEnumValues())
            return;
        
        addUsedJavaType(contentType.getFullJavaName());
        //if (!contentType.isAbstract())
        //    addUsedJavaType(contentType.getFullJavaImplName());
        
        if (contentType.isAnonymousType())
        {
            printReadAttributesMethod(contentType);
            printReadElementsMethod(contentType);
            printWriteAttributesMethod(contentType);
            printWriteElementsMethod(contentType);
        }
        else if (!processedTypes.contains(contentType))
        {
            if (!contentType.isAbstract())
                printReadTypeMethod(contentType);
            printReadAttributesMethod(contentType);
            printReadElementsMethod(contentType);
            
            if (!contentType.isAbstract())
                printWriteTypeMethod(contentType);
            printWriteAttributesMethod(contentType);
            printWriteElementsMethod(contentType);
            
            processedTypes.add(contentType);
        }
    }
    
    
    /*********************************************/
    /**         Read methods generation         **/
    /*********************************************/
        
    void printReadMethodAbstract(SchemaType eltType, SchemaType contentType) throws IOException
    {
        String javaTypeName = contentType.getShortJavaName();
        String eltName = getSchemaComponentLocalName(eltType);
        
        printJavaDoc("Dispatcher method for reading elements derived from " + eltName);
        emit("public " + javaTypeName + " read" + eltName + "(XMLStreamReader reader) throws XMLStreamException");
        
        startBlock();
        emit("String localName = reader.getName().getLocalPart();");
        emit("");
        
        // write dispatcher to derived types
        boolean first = true;
        for (QName subQname: ((SchemaTypeImpl)eltType).getSubstitutionGroupMembers())
        {
            //SchemaGlobalElement elt = eltType.getTypeSystem().findElement(subQname);
            SchemaType elt = eltType.getTypeSystem().findDocumentType(subQname);//.findElement(subQname);
            if (!elt.isAbstract())
            {
                String prefix = "";
                if (!first)
                    prefix = "else ";
                
                //String javaName = elt.getType().getShortJavaName();
                String javaName = elt.getShortJavaName();
                if (javaName.endsWith("Element"))
                    javaName = elt.getContentModel().getType().getShortJavaName();
                    
                emit(prefix + "if (localName.equals(\"" + javaName + "\"))");
                indent();
                emit("return read" + javaName + "(reader);");
                outdent();
                
                first = false; 
            }
        }
        
        emit("");
        emit("throw new XMLStreamException(ERROR_INVALID_ELT + reader.getName() + errorLocationString(reader));");
        
        endBlock();
    }
    
    
    void printReadMethodConcrete(SchemaType eltType, SchemaType contentType) throws IOException
    {
        String javaTypeName = contentType.getShortJavaName();
        String eltName = getSchemaComponentLocalName(eltType);
        
        printJavaDoc("Read method for " + eltName + " elements");
        emit("public " + javaTypeName + " read" + eltName + "(XMLStreamReader reader) throws XMLStreamException");
        if (contentType.isSimpleType())
            javaTypeName = javaWrappedType(javaTypeName);
        
        startBlock();
        
        // validate element name
        emit("boolean found = checkElementName(reader, \"" + eltName + "\");");
        emit("if (!found)");
        indent();
        emit("throw new XMLStreamException(ERROR_INVALID_ELT + reader.getName() + errorLocationString(reader));");
        outdent();
        
        if (contentType.isSimpleType())
        {
            // case of inline value
            emit("");
            emit("String val = reader.getElementText();"); // this positions us on end tag
            emit("if (val != null)");
            indent();
            emit("return " + getInlineValueConversionCall(contentType) + ";");
            outdent();
            emit("else");
            indent();
            emit("return null;");
            outdent();
        }
        else
        {
            // call method to read complex type
            emit("");
            printCallReadType(contentType);
        }
        
        endBlock();
    }
    
    
    void printReadTypeMethod(SchemaType sType) throws IOException
    {
        String javaTypeName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);
        
        printJavaDoc("Read method for " + complexTypeName + " complex type");
        emit("public " + javaTypeName + " read" + complexTypeName + "(XMLStreamReader reader) throws XMLStreamException");
        
        startBlock();
        
        //emit(javaTypeName + " bean = new " + sType.getShortJavaImplName() + "();");
        emit(javaTypeName + " bean = factory.new" + sType.getShortJavaName() + "();");
        
        if (hasAttributes(sType))
        {
            emit("");
            emit("Map<String, String> attrMap = collectAttributes(reader);");
            printCallReadAttributes(sType);
        }
        
        if (hasElements(sType))
        {
            emit("");
            emit("reader.nextTag();"); // go to first property
            printCallReadElements(sType); 
        }
        
        // read text value only if no child elements
        // i.e. mixed content is not suppported
        else if (hasTextValue(sType))
        {
            emit("");
            emit("String val = reader.getElementText();"); // this positions us on end tag
            emit("if (val != null)");
            indent();
            emit("bean.setValue(" + getInlineValueConversionCall(sType) + ");");
            outdent();
        }
        
        emit("");
        emit("return bean;");
        
        endBlock();
    }
    
    
    void printCallReadType(SchemaType sType) throws IOException
    {
        String bindingsInstance = getBindingsVarName(sType);            
        String readMethod = bindingsInstance + ".read" + getSchemaComponentLocalName(sType);
        emit("return " + readMethod + "(reader);");           
    }
    
    
    void printCallReadAttributes(SchemaType sType) throws IOException
    {
        String bindingsInstance = getBindingsVarName(sType);            
        String readMethod = bindingsInstance + ".read" + getSchemaComponentLocalName(sType);
        emit(readMethod + "Attributes(attrMap, bean);");           
    }
    
    
    void printCallReadElements(SchemaType sType) throws IOException
    {
        String bindingsInstance = getBindingsVarName(sType);            
        String readMethod = bindingsInstance + ".read" + getSchemaComponentLocalName(sType);
        emit(readMethod + "Elements(reader, bean);");
    }
    
    
    String getPropertyValueConversionCall(SchemaProperty sProp)
    {
        String propType = javaTypeForProperty(sProp);
        SchemaType sType = sProp.getType();
        return getValueConversionCall(sType, propType);
    }
    
    
    String getInlineValueConversionCall(SchemaType sType)
    {
        SchemaType textValueType = sType;
        while (!textValueType.isSimpleType())
            textValueType = textValueType.getBaseType();
        String javaType = javaTypeForSchemaType(textValueType);
        return getValueConversionCall(sType, javaType);
    }
    
    
    String getValueConversionCall(SchemaType sType, String javaType)
    {
        String valConversion = "val";
        
        if (sType.isSimpleType() && sType.hasStringEnumValues())
        {
            valConversion = sType.getShortJavaName() + ".fromString(val)";
            addUsedJavaType(sType.getFullJavaName());
        }
        else if (javaType.endsWith("[]"))
        {
            valConversion = "get" + NameUtil.upperCamelCase(javaType) + "ArrayFromString(val)";
        }
        else if (javaType.equals("String"))
        {
            valConversion = "trimStringValue(val)"; 
        }
        else
        {
            valConversion = "get" + NameUtil.upperCamelCase(javaType) + "FromString(val)";
        }

        return valConversion;
    }
    
    
    void printReadAttributesMethod(SchemaType sType) throws IOException
    {
        if (!hasAttributes(sType))
            return;
        
        String javaShortName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);
                
        printJavaDoc("Reads attributes of " + complexTypeName + " complex type");
        emit("public void read" + complexTypeName + "Attributes(Map<String, String> attrMap, " + javaShortName + " bean) throws XMLStreamException");
        
        startBlock();
        
        SchemaType baseType = sType.getBaseType();
        if (baseType != null && !baseType.isURType() && !baseType.isSimpleType() && hasAttributes(baseType))
        {
            printCallReadAttributes(baseType);
            emit("");            
        }        
        else if (isExtendedOgcPropertyType(sType))
        {
            emit("readPropertyAttributes(attrMap, bean);");
            emit("");
        }
        
        boolean first = true;
        for (SchemaProperty sProp: sType.getDerivedProperties())
        {
            // skip elements 
            if (!sProp.isAttribute())
                continue;
            
            // skip xlink attributes since we get them from base type
            if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(sProp.getName().getLocalPart()))
                continue;
            
            if (first) {
                emit("String val;");
                first = false;
            }
            
            String propName = sProp.getJavaPropertyName();
            String attName = sProp.getName().getLocalPart();
                                    
            emit("");
            emit("// " + propName.toLowerCase());
            emit("val = attrMap.get(\"" + attName + "\");");
            emit("if (val != null)");
            indent();
            emit("bean.set" + propName + "(" + getPropertyValueConversionCall(sProp) + ");");
            outdent();
        }
        
        endBlock();
    }
    
    
    void printReadElementsMethod(SchemaType sType) throws IOException
    {
        if (!hasElements(sType))
            return;
        
        String javaShortName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);
        
        printJavaDoc("Reads elements of " + complexTypeName + " complex type");
        emit("public void read" + complexTypeName + "Elements(XMLStreamReader reader, " + javaShortName + " bean) throws XMLStreamException");
        
        startBlock();
        
        SchemaType baseType = sType.getBaseType();
        if (baseType != null && !baseType.isURType() && hasElements(baseType))
        {
            printCallReadElements(baseType);
            emit("");
        }
        
        // declare local variables according to types of properties we have
        boolean hasSimpleProps = false;
        for (int i = 0; i < sType.getDerivedProperties().length; i++)
        {
            SchemaProperty sProp = sType.getDerivedProperties()[i];
            if (sProp.isAttribute())
                continue;
            
            if (i == 0)
                emit("boolean found;");
            
            if (!hasSimpleProps && sProp.getJavaTypeCode() != SchemaProperty.XML_OBJECT) {
                emit("String val;");
                hasSimpleProps = true;
            }
        } 
        
        for (SchemaProperty sProp: sType.getDerivedProperties())
        {
            // skip attributes
            if (sProp.isAttribute())
                continue;
            
            emit("");
            String eltName = sProp.getName().getLocalPart();
            
            emit("// " + eltName);
            if (sProp.extendsJavaArray()) {
                emit("do");
                startBlock();
            }
                
            emit("found = checkElementName(reader, \"" + eltName + "\");");
            emit("if (found)");
            startBlock();
            printReadSingleProperty(sProp);            
            emit("reader.nextTag();"); // go to next element
            endBlock(); // end if
            
            if (sProp.extendsJavaArray())
            {
                endBlock(); 
                emit("while (found);");            
            }
        }
        
        endBlock();
    }
    
    
    void printReadSingleProperty(SchemaProperty sProp) throws IOException
    {
        String propName = sProp.getJavaPropertyName();
        String typeName = javaTypeForProperty(sProp);
        String setter = (sProp.extendsJavaArray() ? "add" : "set") + propName;
        String varName = sProp.getName().getLocalPart();
        boolean isOgcProp = isOgcProperty(sProp);
        boolean isOgcComplexProp = isComplexOgcProperty(sProp);
                
        // case of child element (complex type = java object)
        if (sProp.getJavaTypeCode() == SchemaProperty.XML_OBJECT)
        {                
            addUsedJavaType(javaFullTypeForProperty(sProp));            
            
            if (isOgcProp)
            {
                String bindingsInstance = getBindingsVarName(getOgcPropertyElementType(sProp));
                String readMethod = bindingsInstance + ".read" + getPropertyValueTypeLocalName(sProp);
                
                // read name and xlink attributes
                if (isOgcComplexProp)
                {
                    varName += "Prop";
                    String varDecl = OGC_PROP_IFACE_NAME + "<" + typeName + "> " + varName + " = ";
                    
                    if (sProp.extendsJavaArray())
                        emit(varDecl + "new " + OGC_PROP_CLASS_NAME + "<" + typeName + ">();");
                    else 
                    {
                        emit(varDecl + "bean.get" + propName + "Property();");
                        setter = varName + ".setValue";
                    }
                    
                    emit("readPropertyAttributes(reader, " + varName + ");");
                    emit("");
                }
                    
                if (sProp.getType().getContentType() == SchemaType.ELEMENT_CONTENT)
                {
                    emit("reader.nextTag();"); // go to content start tag or property end tag                    
                    emit("if (reader.getEventType() == XMLStreamConstants.START_ELEMENT)");
                    startBlock();
                    if (isChoice(sProp))
                    {
                        printReadChoiceDispatcher(sProp, varName);
                        emit("");
                    }
                    else
                    {
                        if (isOgcComplexProp)
                            emit(varName + ".setValue(" + readMethod + "(reader));");
                        else
                            emit("bean." + setter + "(" + readMethod + "(reader));");
                    }
                    emit("reader.nextTag(); // end property tag"); // go to end property tag
                    endBlock();
                    
                    emit("");
                    if (isOgcComplexProp && sProp.extendsJavaArray())
                        emit("bean.get" + propName + "List().add(" + varName + ");");
                }
                else
                {
                    emit("bean." + setter + "(" + readMethod + "(reader));");
                    if (!hasTextValue(sProp.getType()))
                        emit("reader.nextTag(); // end property tag");
                }
            }
            else
            {
                String bindingsInstance = getBindingsVarName(sProp.getType());
                String readMethod = bindingsInstance + ".read" + sProp.getType().getShortJavaName() + "Type";                
                emit("bean." + setter + "(" + readMethod + "(reader));");
            }
        }
        
        // case of inline value
        else
        {
            emit("val = reader.getElementText();"); // this positions us on end tag
            emit("if (val != null)");
            indent();
            emit("bean." + setter + "(" + getPropertyValueConversionCall(sProp) + ");");
            outdent();
        }        
    }
    
    
    void printReadChoiceDispatcher(SchemaProperty sProp, String varName) throws IOException
    {
        boolean isOgcComplexProp = isComplexOgcProperty(sProp);
        
        emit("String localName = reader.getName().getLocalPart();");
        emit("");
        
        // write dispatcher to derived types
        boolean first = true;
        SchemaProperty[] choiceProps = sProp.getType().getElementProperties();
        for (SchemaProperty item: choiceProps)
        {                
            String prefix = "";
            if (!first)
                prefix = "else ";
            
            // use choice property "As" name
            String setter = (sProp.extendsJavaArray() ? "add" : "set") + sProp.getJavaPropertyName();// + "As" + item.getJavaPropertyName();
            String choiceType = javaTypeForSchemaType(item.javaBasedOnType());
            addUsedJavaType(javaFullTypeForSchemaType(item.javaBasedOnType()));
            String eltLocalName = NameUtil.upperCamelCase(item.getName().getLocalPart());
            
            emit(prefix + "if (localName.equals(\"" + choiceType + "\"))");
            startBlock();
            String bindingsInstance = getBindingsVarName(item.javaBasedOnType());
            emit(choiceType + " " + varName + " = " + bindingsInstance + ".read" + eltLocalName + "(reader);");                
            if (isOgcComplexProp)
                emit(varName + ".setValue(" + varName + ");");
            else
                emit("bean." + setter + "(" + varName + ");");
            endBlock();
            
            first = false;
        }
        
        emit("else");
        indent();
        emit("throw new XMLStreamException(ERROR_INVALID_ELT + reader.getName() + errorLocationString(reader));");
        outdent();
    }
    
    
    /*********************************************/
    /**         Write methods generation        **/
    /*********************************************/   
    
    void printWriteMethodAbstract(SchemaType eltType, SchemaType contentType) throws IOException
    {
        String javaTypeName = contentType.getShortJavaName();
        String eltName = getSchemaComponentLocalName(eltType);
        
        printJavaDoc("Dispatcher method for writing classes derived from " + eltName);
        emit("public void write" + eltName + "(XMLStreamWriter writer, " + javaTypeName + " bean) throws XMLStreamException");
        
        startBlock();
        
        // write dispatcher to derived types
        boolean first = true;
        for (QName subQname: ((SchemaTypeImpl)eltType).getSubstitutionGroupMembers())
        {
            //SchemaGlobalElement elt = eltType.getTypeSystem().findElement(subQname);
            SchemaType elt = eltType.getTypeSystem().findDocumentType(subQname);//.findElement(subQname);
            if (!elt.isAbstract())
            {
                String prefix = "";
                if (!first)
                    prefix = "else ";
                
                //String javaName = elt.getType().getShortJavaName();
                String javaName = elt.getShortJavaName();
                if (javaName.endsWith("Element"))
                    javaName = elt.getContentModel().getType().getShortJavaName();
                else
                    addUsedJavaType(elt.getFullJavaName());
                
                emit(prefix + "if (bean instanceof " + javaName + ")");
                indent();
                emit("write" + javaName + "(writer, (" + javaName + ")bean);");
                outdent();
                
                first = false; 
            }
        }
        
        if (((SchemaTypeImpl)eltType).getSubstitutionGroupMembers().length > 0)
        {
            emit("else");
            indent();
            emit("throw new XMLStreamException(ERROR_UNSUPPORTED_TYPE + bean.getClass().getCanonicalName());");
            outdent();
        }
        
        endBlock();
    }
    
    
    void printWriteMethodConcrete(SchemaType eltType, SchemaType contentType) throws IOException
    {
        String javaTypeName = contentType.getShortJavaName();
        String eltName = getSchemaComponentLocalName(eltType);
        
        printJavaDoc("Write method for " + eltName + " element");
        emit("public void write" + eltName + "(XMLStreamWriter writer, " + javaTypeName + " bean) throws XMLStreamException");
        
        startBlock();        
        emit("writer.writeStartElement(NS_URI, \"" + eltName + "\");");
        
        // add namespace declarations if needed
        emit("this.writeNamespaces(writer);");
        
        if (contentType.isSimpleType())
        {
            // write inline value
            String getStringCall;
            if (javaTypeForSchemaType(contentType).equals(String.class.getSimpleName()))
                getStringCall = "bean";
            else
                getStringCall = "getStringValue(bean)";
            emit("writer.writeCharacters(" + getStringCall + ");");
        }
        else
        {
            printCallWriteType(contentType);
        }
        
        emit("writer.writeEndElement();");        
        endBlock();        
    }
    
    
    void printWriteTypeMethod(SchemaType sType) throws IOException
    {
        String javaTypeName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);        
        
        printJavaDoc("Write method for " + complexTypeName + " complex type");
        emit("public void write" + complexTypeName + "(XMLStreamWriter writer, " + javaTypeName + " bean) throws XMLStreamException");
        
        startBlock();
        
        if (hasAttributes(sType))
            printCallWriteAttributes(sType);
        
        if (hasElements(sType))
            printCallWriteElements(sType);
        
        // write text value only if no child elements
        // i.e. mixed content is not suppported
        else if (hasTextValue(sType))
        {
            emit("");
            String getStringCall = "getStringValue(bean.getValue())";
            emit("writer.writeCharacters(" + getStringCall + ");");
        }
        
        endBlock();
    }
    
    
    void printCallWriteType(SchemaType sType) throws IOException
    {
        String bindingsInstance = getBindingsVarName(sType);            
        String writeMethod = bindingsInstance + ".write" + getSchemaComponentLocalName(sType);
        emit(writeMethod + "(writer, bean);");           
    }
    
    
    void printCallWriteAttributes(SchemaType sType) throws IOException
    {
        String bindingsInstance = getBindingsVarName(sType);            
        String writeMethod = bindingsInstance + ".write" + getSchemaComponentLocalName(sType);
        emit(writeMethod + "Attributes(writer, bean);");
    }
    
    
    void printCallWriteElements(SchemaType sType) throws IOException
    {              
        String bindingsInstance = getBindingsVarName(sType);            
        String writeMethod = bindingsInstance + ".write" + getSchemaComponentLocalName(sType);
        emit(writeMethod + "Elements(writer, bean);");
    }
    
    
    void printWriteAttributesMethod(SchemaType sType) throws IOException
    {
        if (!hasAttributes(sType))
            return;
        
        String shortJavaName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);
         
        printJavaDoc("Writes attributes of " + complexTypeName + " complex type");
        emit("public void write" + complexTypeName + "Attributes(XMLStreamWriter writer, " + shortJavaName + " bean) throws XMLStreamException");
        
        startBlock();
        
        SchemaType baseType = sType.getBaseType();
        if (baseType != null && !baseType.isURType() && !baseType.isSimpleType() && hasAttributes(baseType))
            printCallWriteAttributes(baseType);
        else if (isExtendedOgcPropertyType(sType))
            emit("writePropertyAttributes(writer, bean);");
        
        printWriteAttributeProperties(sType);
        endBlock();
    }
    
    
    void printWriteElementsMethod(SchemaType sType) throws IOException
    {
        if (!hasElements(sType))
            return;
        
        String shortJavaName = sType.getShortJavaName();
        String complexTypeName = getSchemaComponentLocalName(sType);
        
        printJavaDoc("Writes elements of " + complexTypeName + " complex type");
        emit("public void write" + complexTypeName + "Elements(XMLStreamWriter writer, " + shortJavaName + " bean) throws XMLStreamException");
        
        startBlock();
        
        SchemaType baseType = sType.getBaseType();
        if (baseType != null && !baseType.isURType() && hasElements(baseType))
            printCallWriteElements(baseType);
        
        printWriteElementProperties(sType);
        endBlock();
    }
    
    
    void printWriteAttributeProperties(SchemaType sType) throws IOException
    {        
        for (SchemaProperty sProp: sType.getDerivedProperties())
        {
            // skip elements 
            if (!sProp.isAttribute())
                continue;
            
            // skip xlink attributes since we get them from base type
            if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(sProp.getName().getLocalPart()))
                continue; 
            
            emit("");
            emit("// " + sProp.getName().getLocalPart());
            if (sProp.extendsJavaOption())
                printWriteOptionalAttributeProperty(sProp);
            else
                printWriteAttributeProperty(sProp);
        }
    }
    
    
    void printWriteElementProperties(SchemaType sType) throws IOException
    {       
        // declare local variables according to types of properties we have
        for (int i = 0; i < sType.getDerivedProperties().length; i++)
        {
            SchemaProperty sProp = sType.getDerivedProperties()[i];
            if (sProp.isAttribute())
                continue;
            
            if (sProp.extendsJavaArray()) {
                emit("int numItems;");
                break;
            }
        } 
        
        for (SchemaProperty sProp: sType.getDerivedProperties())
        {
            // skip attributes 
            if (sProp.isAttribute())
                continue;
            
            emit("");
            emit("// " + sProp.getName().getLocalPart());
            if (sProp.extendsJavaArray())
            {
                //if (sProp.getJavaTypeCode() == SchemaProperty.JAVA_LIST)
                //    printWritePropertyArrayElt(sProp);
                printWritePropertyList(sProp);                
            }
            else if (sProp.extendsJavaOption())
                printWriteOptionalProperty(sProp);
            else
                printWriteSingleProperty(sProp);
        }
    }
    
    
    void printWriteAttributeProperty(SchemaProperty sProp) throws IOException
    {
        String attName = sProp.getName().getLocalPart();
        String getMethod = sProp.getJavaPropertyName();        
        emit("writer.writeAttribute(\"" + attName + "\", getStringValue(bean.get" + getMethod + "()));");
    }
    
    
    void printWriteOptionalAttributeProperty(SchemaProperty sProp) throws IOException
    {
        String isSetMethod = sProp.getJavaPropertyName();
        emit("if (bean.isSet" + isSetMethod + "())");
        indent();
        printWriteAttributeProperty(sProp);
        outdent();
    }
    
    
    void printWriteSingleProperty(SchemaProperty sProp) throws IOException
    {
        QName qname = sProp.getName();
        String propName = sProp.getJavaPropertyName();
        boolean isComplexOgcProperty = isComplexOgcProperty(sProp);
        
        emit("writer.writeStartElement(NS_URI, \"" + qname.getLocalPart() + "\");");
        
        String accessCall = "bean.get" + propName + "()";
        if (sProp.extendsJavaArray())
        {
            if (isComplexOgcProperty)
                accessCall = "item.getValue()";
            else
                accessCall = "item";
        }
        
        //if (SchemaTypeCodePrinter.isComplexOgcProperty(sProp))
        //    emit("writePropertyAttributes(bean);");
        
        if (sProp.getJavaTypeCode() == SchemaProperty.XML_OBJECT)
        {                
            // write name and xlink attributes
            if (isComplexOgcProperty)
            {
                if (sProp.extendsJavaArray())
                {
                    emit("writePropertyAttributes(writer, item);");
                    if (isChoice(sProp))
                        emit("");
                    emit("if (item.hasValue() && !item.hasHref())");
                }
                else
                {
                    String typeName = javaTypeForProperty(sProp);
                    String varName = sProp.getName().getLocalPart() + "Prop";
                    String varDecl = OGC_PROP_IFACE_NAME + "<" + typeName + "> " + varName + " = ";
                    emit(varDecl + "bean.get" + propName + "Property();");
                    emit("writePropertyAttributes(writer, " + varName + ");");
                    if (isChoice(sProp))
                        emit("");
                    emit("if (" + varName + ".hasValue() && !" + varName + ".hasHref())");
                }
                
                if (isChoice(sProp))
                    startBlock();
                else
                    indent();
            }
            
            if (isChoice(sProp))
            {
                if (!isComplexOgcProperty)
                    emit("");
                
                // write dispatcher to possible types
                boolean first = true;
                SchemaProperty[] choiceProps = sProp.getType().getElementProperties();
                for (SchemaProperty item: choiceProps)
                {                
                    String prefix = "";
                    if (!first)
                        prefix = "else ";
                    
                    String choiceType = item.javaBasedOnType().getShortJavaName();
                    String eltLocalName = NameUtil.upperCamelCase(item.getName().getLocalPart());
                    String bindingsInstance = getBindingsVarName(item.javaBasedOnType());
                    String writeMethod = bindingsInstance + ".write" + eltLocalName;
                    
                    emit(prefix + "if (" + accessCall + " instanceof " + choiceType + ")");
                    indent();
                    emit(writeMethod + "(writer, (" + choiceType + ")" + accessCall + ");");
                    outdent();
                    
                    first = false;
                }
                
                //emit("else");
                //indent();
                //emit("throw new XMLStreamException(ERROR_INVALID_ELT + reader.getName() + errorLocationString(reader));");
                //outdent();
                
                if (!isComplexOgcProperty)
                    emit("");
            }
            else
            {
                String bindingsInstance = getBindingsVarName(getOgcPropertyElementType(sProp));
                String writeMethod = bindingsInstance + ".write" + getPropertyValueTypeLocalName(sProp);
                emit(writeMethod + "(writer, " + accessCall + ");");
            }
            
            if (isComplexOgcProperty(sProp))
            {
                if (isChoice(sProp))
                {
                    endBlock();
                    emit("");
                }
                else
                    outdent();
            }
        }
        else
        {
            // case of inline value
            String getStringCall = "getStringValue(" + accessCall + ")";
            if (javaTypeForProperty(sProp).equals(String.class.getSimpleName()))
                getStringCall = accessCall;
            emit("writer.writeCharacters(" + getStringCall + ");");
        }
        
        emit("writer.writeEndElement();");
    }
    
    
    void printWriteOptionalProperty(SchemaProperty sProp) throws IOException
    {
        String isSetMethod = sProp.getJavaPropertyName();
        emit("if (bean.isSet" + isSetMethod + "())");
        startBlock();
        printWriteSingleProperty(sProp);
        endBlock();
    }
    
    
    void printWritePropertyList(SchemaProperty sProp) throws IOException
    {
        String propType = javaTypeForProperty(sProp);
        addUsedJavaType(javaFullTypeForProperty(sProp));
        String getListCall = "bean.get" + sProp.getJavaPropertyName() + "List()";
        emit("numItems = " + getListCall + ".size();");
        emit("for (int i = 0; i < numItems; i++)");
        startBlock();
        if (isComplexOgcProperty(sProp))
            emit(OGC_PROP_IFACE_NAME + "<" + propType + "> item = " + getListCall + ".getProperty(i);");
        else
            emit(propType + " item = " + getListCall + ".get(i);");
        printWriteSingleProperty(sProp);
        endBlock();
    }
    
    
    void printWritePropertyArrayElt(SchemaProperty sProp) throws IOException
    {
        QName qname = sProp.getName();
        
        emit("writer.writeStartElement(NS_URI, \"" + qname.getLocalPart() + "\");");
        
        String getCall = "bean.get" + sProp.getJavaPropertyName() + "Array()";        
        emit("writer.writeCharacters(getStringValue(" + getCall + "));");
    
        emit("writer.writeEndElement();");
    }
    
        
    /* **********************************/
    /*         Utility Methods          */
    /* **********************************/    
    
    public static String getBindingsFullClassName(String packageName)
    {
        return packageName + "." + BINDING_SUBPACKAGE_NAME + "." + BINDING_CLASS_NAME;
    }
    
}
