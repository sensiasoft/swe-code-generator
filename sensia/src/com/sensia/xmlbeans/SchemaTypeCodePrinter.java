package com.sensia.xmlbeans;

import java.io.CharArrayWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;
import org.apache.xmlbeans.impl.common.NameUtil;
import org.apache.xmlbeans.impl.schema.SchemaPropertyImpl;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.SchemaProperty;
import org.apache.xmlbeans.SchemaStringEnumEntry;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.SchemaCodePrinter;


/**
 * Prints the java code for a single schema type
 */
public final class SchemaTypeCodePrinter extends AbstractCodePrinter implements SchemaCodePrinter
{
    CharArrayWriter charBuffer = new CharArrayWriter(1024);    
        

    public SchemaTypeCodePrinter()
    {
        _indent = 0;
    }


    public void setOptions(XmlOptions opt)
    {
        
    }


    public void printType(Writer writer, SchemaType sType) throws IOException
    {
        charBuffer.reset();
        usedJavaTypes.clear();
        
        // print in memory buffer until we know what imports to add
        _writer = charBuffer;
        printInnerType(sType, sType.getTypeSystem());
        
        // now write everything to original writer
        _writer = writer;
        printPackage(sType, true);
        
        // import statements
        emit("");
        for (String type: getImportedTypes(sType, false))
            emit("import " + type + ";"); 
        
        charBuffer.writeTo(writer);        
        _writer.flush();
    }


    public void printTypeImpl(Writer writer, SchemaType sType) throws IOException
    {
        // don't create implementation for wildcard types
        if (sType.isURType())
            return;
        
        charBuffer.reset();
        usedJavaTypes.clear();
        
        // print in memory buffer until we know what imports to add
        _writer = charBuffer;
        printInnerTypeImpl(sType, sType.getTypeSystem(), false);
        
        // now write everything to original writer
        _writer = writer;
        printPackage(sType, false);
        emit("");
        
        // import statements
        for (String type: getImportedTypes(sType, true))
            emit("import " + type + ";");
        
        charBuffer.writeTo(writer);        
        _writer.flush();
    }
    
    
    public List<String> getImportedTypes(SchemaType sType, boolean impl)
    {
        String currentPackage = getJavaPackage(sType, impl);
                
        List<String> importTypes = new ArrayList<String>();
        
        // select types to import
        for (String fullJavaType: usedJavaTypes)
        {            
            if (fullJavaType == null)
                continue;
            
            // skip java.lang types
            if (fullJavaType.startsWith("java.lang"))
                continue;
            
            // skip primitive types
            if (fullJavaType.indexOf('.') < 0)
                continue;
            
            // skip types in same package
            if (getJavaPackage(fullJavaType, impl).equals(currentPackage))
                continue;
            
            // remove generic part
            int genericIndex = fullJavaType.indexOf('<');
            if (genericIndex > 0)
                fullJavaType = fullJavaType.substring(0, genericIndex);
            
            // correct last dot in case of inner class
            fullJavaType = fullJavaType.replace('$', '.');
            
            // only add the same type once!
            if (!importTypes.contains(fullJavaType))
                importTypes.add(fullJavaType);
            
            if (impl)
            {
                if (importTypes.contains(OGC_PROP_PACKAGE_NAME + OGC_PROP_IFACE_NAME) &&
                    !importTypes.contains(OGC_PROP_PACKAGE_NAME + OGC_PROP_CLASS_NAME))
                    importTypes.add(OGC_PROP_PACKAGE_NAME + OGC_PROP_CLASS_NAME);
            }
        }
        
        // reorder
        Collections.sort(importTypes);
        return importTypes;
    }


    static String prettyQName(QName qname)
    {
        String result = qname.getLocalPart();
        if (qname.getNamespaceURI() != null)
            result += "(@" + qname.getNamespaceURI() + ")";
        return result;
    }


    void printInnerTypeJavaDoc(SchemaType sType) throws IOException
    {
        QName name = sType.getName();
        if (name == null)
        {
            if (sType.isDocumentType())
                name = sType.getDocumentElementName();
            else if (sType.isAttributeType())
                name = sType.getAttributeTypeAttributeName();
            else if (sType.getContainerField() != null)
                name = sType.getContainerField().getName();
        }

        emit("");
        emit("/**");
        if (sType.isDocumentType())
            emit(" * A document containing one " + prettyQName(name) + " element.");
        else if (sType.isAttributeType())
            emit(" * A document containing one " + prettyQName(name) + " attribute.");
        else if (name != null)
            emit(" * POJO class for XML type " + prettyQName(name) + ".");
        else
            emit(" * An anonymous inner XML type.");
        emit(" *");
        switch (sType.getSimpleVariety())
        {
            case SchemaType.NOT_SIMPLE:
                emit(" * This is a complex type.");
                break;
            case SchemaType.ATOMIC:
                //emit(" * This is an atomic type that is a restriction of " + getFullJavaName(sType) + ".");
                break;
            case SchemaType.LIST:
                emit(" * This is a list type whose items are " + sType.getListItemType().getFullJavaName() + ".");
                break;
            case SchemaType.UNION:
                emit(" * This is a union type. Instances are of one of the following types:");
                SchemaType[] members = sType.getUnionConstituentTypes();
                for (int i = 0; i < members.length; i++)
                    emit(" *     " + members[i].getFullJavaName());
                break;
        }
        emit(" */");
    }
    
    
    SchemaProperty[] getDocumentTypeProperties(SchemaType sType)
    {
        SchemaType eltType = sType.getDerivedProperties()[0].getType();
        if (eltType.isAnonymousType())
           return eltType.getDerivedProperties();
        else
            return new  SchemaProperty[0];
    }


    void printInnerType(SchemaType sType, SchemaTypeSystem system) throws IOException
    {
        emit("");

        printInnerTypeJavaDoc(sType);
        
        if (sType.isSimpleType())
        {
            if (sType.hasStringEnumValues())
                printStringEnumClass(sType);
            return;
        }        
        
        
        startInterface(sType);        
        
        {
            if (sType.hasStringEnumValues())
            {
                printStringEnumeration(sType);
            }

            else if (sType.getSimpleVariety() == SchemaType.UNION)
            {
                
            }
            
            else if (isChoice(sType))
            {
                
            }
    
            else
            {
                // get type properties
                // if type is global element, process sub-properties of first property
                SchemaProperty[] properties;
                if (sType.isDocumentType())
                    properties = getDocumentTypeProperties(sType);
                else
                    properties = getDerivedProperties(sType);
                
                for (SchemaProperty prop: properties)
                {
                    // skip xlink properties since we get them from base type
                    if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(prop.getName().getLocalPart()))
                        continue;                    
                    
                    // case of choice
                    if (isChoice(prop))
                    {
                        // generate only one set of getters with common base type + unset
                        // common type is inferred in javaTypeForProperty
                        printPropertyGetters(prop);
                        if (prop.extendsJavaOption() && hasJavaPrimitiveType(prop))
                            printPropertyUnSetSignature(prop, false);
                        
                        boolean isComplexOgcProp = isComplexOgcProperty(prop);
                        
                        // generate setter for each possible type
                        if (!prop.isReadOnly())
                        {
                            SchemaProperty[] choiceProps = prop.getType().getElementProperties();
                            for (SchemaProperty item: choiceProps)
                            {
                                if (isComplexOgcProp)
                                    ((SchemaPropertyImpl)item).setNillable(OGC_PROP_NILLABLE_CODE);
                                                                
                                String backupName = item.getJavaPropertyName();
                                String newName = prop.getJavaPropertyName();// + "As" + item.getJavaPropertyName();
                                ((SchemaPropertyImpl)item).setJavaPropertyName(newName);
                                ((SchemaPropertyImpl)item).setExtendsJava(
                                        item.javaBasedOnType().getRef(),
                                        prop.extendsJavaSingleton(),
                                        prop.extendsJavaOption(),
                                        prop.extendsJavaArray());
                                
                                printPropertySetterSignature(item, false);
                                ((SchemaPropertyImpl)item).setJavaPropertyName(backupName);
                            }
                        }
                    }                    
                    else
                    {
                        printPropertyGetters(prop);
                        if (!prop.isReadOnly())
                            printPropertySetters(prop);
                    }
                }
            }
            
            // if atomic simple content, also generate get/set for inline value
            // unless base type already takes care of it
            if (sType.getSimpleVariety() == SchemaType.ATOMIC && !MySchemaTypeSystemCompiler.isGenerated(sType.getBaseType()))
            {                
                // TODO handle case of enumeration?          
                printSimpleTypeGetterSignature(sType, false);
                printSimpleTypeSetterSignature(sType, false);
            }
        }

        printNestedTypes(sType, system);
        endBlock();
    }
    
    
    void printPropertyGetters(SchemaProperty sProp) throws IOException
    {
        printPropertyGetterSignature(sProp, false);
        
        if (sProp.extendsJavaArray())
        {
            printPropertyGetNumSignature(sProp, false);
            
            if (isOgcProperty(sProp) && hasName(sProp))
                printNamedPropertyGetterSignature(sProp, false);
        }
        else
        {
            if (isComplexOgcProperty(sProp))
                printOgcPropertyGetterSignature(sProp, false);
            
            if (sProp.extendsJavaOption())
                printPropertyIsSetSignature(sProp, false);
        }
    }    


    void printPropertySetters(SchemaProperty sProp) throws IOException
    {
        printPropertySetterSignature(sProp, false);
        //if (sProp.extendsJavaArray())
        //    printPropertyAddNewSignature(sProp, false);
        if (sProp.extendsJavaOption() && hasJavaPrimitiveType(sProp))
            printPropertyUnSetSignature(sProp, false);
    }
    
    
    void printNestedTypes(SchemaType sType, SchemaTypeSystem system) throws IOException
    {
        for (SchemaType anonType: sType.getAnonymousTypes())
        {
            SchemaProperty[] eltProps = anonType.getElementProperties();
            if (eltProps.length > 0)
            {
                anonType = eltProps[0].getType();
                if (!anonType.isAnonymousType())
                    continue;
            }
            
            if (anonType.isSkippedAnonymousType())
                printNestedTypes(anonType, system);
            else
                printInnerType(anonType, system);
        }
    }
    

    void printPackage(SchemaType sType, boolean intf) throws IOException
    {
        String fqjn;
        if (intf)
            fqjn = sType.getFullJavaName();
        else
            fqjn = sType.getFullJavaImplName();

        int lastdot = fqjn.lastIndexOf('.');
        if (lastdot < 0)
            return;
        String pkg = fqjn.substring(0, lastdot);
        emit("package " + pkg + ";");
    }


    void startInterface(SchemaType sType) throws IOException
    {
        String shortName = sType.getShortJavaName();
        String extendsStatement = "";
        String baseInterface;
        
        // for global elements, we use the content xml type as base
        if (sType.isDocumentType())
        {
            baseInterface = findJavaType(sType.getContentModel().getType());
            addUsedJavaType(baseInterface);
            baseInterface = baseInterface.substring(baseInterface.lastIndexOf('.')+1);
        }
        
        // otherwise just use the base type
        else
        {
            baseInterface = findJavaType(sType.getBaseType());
            if (sType.getBaseType().isSimpleType() || sType.getBaseType().isURType())
            {
                if (isExtendedOgcPropertyType(sType))
                    baseInterface = OGC_PROP_PACKAGE_NAME + OGC_PROP_IFACE_NAME + "<Object>";
                else
                    baseInterface = "";
            }
            else
            {
                addUsedJavaType(baseInterface);
                baseInterface = baseInterface.substring(baseInterface.lastIndexOf('.')+1);
            }
        }
        
        if (baseInterface.length() > 0)
            extendsStatement = " extends " + baseInterface;        

        emit("public interface " + shortName + extendsStatement);
        emit("{");
        indent();
    }
    enum MyEnum { A, B, C, D};
    

    void printStringEnumClass(SchemaType sType) throws IOException
    {
        //SchemaType baseEnumType = sType.getBaseEnumType();
        String shortName = sType.getShortJavaName();
        
        emit("public enum " + shortName);
        startBlock();
        
        SchemaStringEnumEntry[] items = sType.getStringEnumEntries();
        for (int i=0; i<items.length; i++)
        {
            SchemaStringEnumEntry entry = items[i];
            String endLine = i == items.length - 1 ? ";" : "," ;
            emit(entry.getEnumName() + "(\"" + entry.getString() + "\")" + endLine);
        }
        
        // text field
        emit("");
        emit("private final String text;");
        
        // private constructor
        emit("");
        printJavaDoc("Private constructor for storing string representation", false);
        emit("private " + shortName + "(String s)");
        startBlock();
        emit("this.text = s;");
        endBlock();
        
        // toString
        emit("");
        printJavaDoc("To convert an enum constant to its String representation", false);
        emit("public String toString()");
        startBlock();
        emit("return text;");
        endBlock();
        
        // fromString
        emit("");
        printJavaDoc("To get the enum constant corresponding to the given String representation", false);
        emit("public static " + shortName + " fromString(String s)");
        startBlock();
        for (int i=0; i<items.length; i++)
        {
            SchemaStringEnumEntry entry = items[i];
            String prefix = i == 0 ? "" : "else ";
            emit(prefix + "if (s.equals(\"" + entry.getString() + "\"))");
            indent();
            emit("return " + entry.getEnumName() + ";");
            outdent();            
        }
        emit("");
        emit("throw new IllegalArgumentException(\"Invalid token \" + s + \" for enum " + shortName + "\");");
        endBlock();
        
        endBlock();
    }
    
    
    void printStringEnumeration(SchemaType sType) throws IOException
    {
        
    }
    
    
    void printPropertyGetterSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String type = javaTypeForProperty(sProp);
        boolean several = sProp.extendsJavaArray();
        String endDecl = impl ? "" : ";";

        if (several)
        {
            String listType = javaListTypeForProperty(sProp, false);
            printJavaDoc("Gets the list of " + NameUtil.lowerCamelCase(propertyName) + " properties",
                         null,
                         "property list",
                         impl);
            emit("public " + listType + " get" + propertyName + "List()" + endDecl);
        }
        else
        {
            printJavaDoc("Gets the " + NameUtil.lowerCamelCase(propertyName) + " property",
                         null,
                         "property value",
                         impl);
            emit("public " + type + " get" + propertyName + "()" + endDecl);
        }
    }
    
    
    void printOgcPropertyGetterSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String type = javaTypeForProperty(sProp);
        String endDecl = impl ? "" : ";";
        
        printJavaDoc("Gets extra info (name, xlink, etc.) carried by the " + NameUtil.lowerCamelCase(propertyName) + " property",
                     null,
                     "property object",
                     impl);;
        emit("public OgcProperty<" + type + "> get" + propertyName + "Property()" + endDecl);
    }
    
    
    void printNamedPropertyGetterSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String type = javaTypeForProperty(sProp);
        String endDecl = impl ? "" : ";";
        
        printJavaDoc("Gets the " + NameUtil.lowerCamelCase(propertyName) + " property with the given name",
                     new String[] {"name name of property to retrieve"},
                     "property value",
                     impl);
        emit("public " + type + " get" + propertyName + "(String name)" + endDecl);
    }
    
    
    void printPropertyIsSetSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String endDecl = impl ? "" : ";";

        printJavaDoc("Checks if " + NameUtil.lowerCamelCase(propertyName) + " is set",
                     null,
                     "true if set, false otherwise",
                     impl);
        emit("public boolean isSet" + propertyName + "()" + endDecl);
    }


    void printPropertyGetNumSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String endDecl = impl ? "" : ";";
        String plural = propertyName.endsWith("s") ? "" : "s";
        
        printJavaDoc("Returns number of " + NameUtil.lowerCamelCase(propertyName) + " properties",
                     null,
                     "number of properties",
                     impl);
        emit("public int getNum" + propertyName + plural + "()" + endDecl);
    }


    void printPropertySetterSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String type = javaTypeForProperty(sProp);
        boolean several = sProp.extendsJavaArray();
        String endDecl = impl ? "" : ";";
        String safeVarName = javaVarNameForProperty(sProp);
                
        if (several)
        {
            String itemName = safeVarName.replace("List", "");
            String javadocText = "Adds a new " + NameUtil.lowerCamelCase(propertyName) + " property";
            
            if (isOgcProperty(sProp) && hasName(sProp))
            {
                printJavaDoc(javadocText,
                             new String[] {"name name of property to add", itemName + " property value"},
                             null,
                             impl);
                emit("public void add" + propertyName + "(String name, " + type + " " + itemName + ")" + endDecl);
            }
            else
            {
                printJavaDoc(javadocText,
                             new String[] {itemName + " property value"},
                             null,
                             impl); 
                emit("public void add" + propertyName + "(" + type + " " + itemName + ")" + endDecl);
            }
        }
        else
        {
            String javadocText = "Sets the " + NameUtil.lowerCamelCase(propertyName) + " property";
                                
            if (isOgcProperty(sProp) && hasName(sProp))
            {
                printJavaDoc(javadocText,
                             new String[] {"name name of property to set", safeVarName + " property value"},
                             null,
                             impl);
                emit("public void set" + propertyName + "(String name, " + type + " " + safeVarName + ")" + endDecl);
            }
            else
            {
                printJavaDoc(javadocText,
                             new String[] {safeVarName + " property value"},
                             null,
                             impl);
           emit("public void set" + propertyName + "(" + type + " " + safeVarName + ")" + endDecl);
            }
        }
    }


    void printPropertyAddNewSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String type = javaTypeForProperty(sProp);
        String endDecl = impl ? "" : ";";
        
        printJavaDoc("Adds and returns a new empty " + NameUtil.lowerCamelCase(propertyName), impl);
        emit("public " + type + " addNew" + propertyName + "()" + endDecl);
    }
    
    
    void printPropertyUnSetSignature(SchemaProperty sProp, boolean impl) throws IOException
    {
        String propertyName = sProp.getJavaPropertyName();
        String endDecl = impl ? "" : ";";

        printJavaDoc("Unsets the " + NameUtil.lowerCamelCase(propertyName) + " property", impl);
        emit("public void unSet" + propertyName + "()" + endDecl);
    }
    
    
    void printSimpleTypeGetterSignature(SchemaType sType, boolean impl) throws IOException
    {
        String javaType = javaTypeForSchemaType(sType.getBaseType());
        String endDecl = impl ? "" : ";";
        
        printJavaDoc("Gets the inline value", null, "property value", impl);
        emit("public " + javaType + " getValue()" + endDecl);
    }
          
    
    void printSimpleTypeSetterSignature(SchemaType sType, boolean impl) throws IOException
    {
        String javaType = javaTypeForSchemaType(sType.getBaseType());
        String endDecl = impl ? "" : ";";
        
        printJavaDoc("Sets the inline value", new String[] {"value property value"}, null, impl);
        emit("public void setValue(" + javaType + " value)" + endDecl);
    }
    
    
    void printSimpleTypeGetSetImpls(SchemaType sType) throws IOException
    {
        // TODO handle case of enumeration?          
        printSimpleTypeGetterSignature(sType, true);
        startBlock();
        emit("return value;");
        endBlock();
        
        printSimpleTypeSetterSignature(sType, true);
        startBlock();
        emit("this.value = value;");
        endBlock();
    }


    void startClass(SchemaType sType, boolean isInner) throws IOException
    {
        String shortName = sType.getShortJavaImplName();
        
        // parent interfaces
        StringBuffer interfaces = new StringBuffer();        
        addUsedJavaType(sType.getFullJavaName());
        interfaces.append(sType.getShortJavaName().replace('$', '.'));

        if (sType.getSimpleVariety() == SchemaType.UNION)
        {
            SchemaType[] memberTypes = sType.getUnionMemberTypes();
            for (int i = 0; i < memberTypes.length; i++)
            {
                addUsedJavaType(memberTypes[i].getFullJavaName());
                interfaces.append(", " + memberTypes[i].getShortJavaName().replace('$', '.'));
            }
        }
        
        // parent class
        boolean isAbstract = sType.isAbstract();
        String baseClass;
        
        if (sType.isDocumentType())
        {
            SchemaType baseType = sType.getContentModel().getType();
            addUsedJavaType(baseType.getFullJavaImplName());
            baseClass = baseType.getShortJavaImplName();
        }
        else
            baseClass = getBaseClass(sType);
        
        String extendsStatement = "";
        if (baseClass != null)
            extendsStatement = " extends " + baseClass;
        else if (isExtendedOgcPropertyType(sType))
            extendsStatement = " extends " + OGC_PROP_PACKAGE_NAME + OGC_PROP_CLASS_NAME + "<Object>";
        
        emit("public " + (isInner ? "static " : "") + (isAbstract ? "abstract " : "") + "class " + shortName + extendsStatement + " implements " + interfaces.toString());

        startBlock();

        emit("static final long serialVersionUID = 1L;");
    }


    void makeMissingValue(int javaType) throws IOException
    {
        switch (javaType)
        {
            case SchemaProperty.JAVA_BOOLEAN:
                emit("return false;");
                break;

            case SchemaProperty.JAVA_FLOAT:
                emit("return 0.0f;");
                break;

            case SchemaProperty.JAVA_DOUBLE:
                emit("return 0.0;");
                break;

            case SchemaProperty.JAVA_BYTE:
            case SchemaProperty.JAVA_SHORT:
            case SchemaProperty.JAVA_INT:
                emit("return 0;");
                break;

            case SchemaProperty.JAVA_LONG:
                emit("return 0L;");
                break;

            default:
            case SchemaProperty.XML_OBJECT:
            case SchemaProperty.JAVA_BIG_DECIMAL:
            case SchemaProperty.JAVA_BIG_INTEGER:
            case SchemaProperty.JAVA_STRING:
            case SchemaProperty.JAVA_BYTE_ARRAY:
            case SchemaProperty.JAVA_GDATE:
            case SchemaProperty.JAVA_GDURATION:
            case SchemaProperty.JAVA_DATE:
            case SchemaProperty.JAVA_QNAME:
            case SchemaProperty.JAVA_LIST:
            case SchemaProperty.JAVA_CALENDAR:
            case SchemaProperty.JAVA_ENUM:
            case SchemaProperty.JAVA_OBJECT:
                emit("return null;");
                break;
        }
    }


    void printInnerTypeImpl(SchemaType sType, SchemaTypeSystem system, boolean isInner) throws IOException
    {
        String shortName = sType.getShortJavaImplName();

        emit("");
        printInnerTypeJavaDoc(sType);

        startClass(sType, isInner);

        if (!sType.isSimpleType())
        {
            // get type properties
            // if type is global element, process sub-properties of first property
            SchemaProperty[] properties;
            if (sType.isDocumentType())
                properties = getDocumentTypeProperties(sType);
            else
                properties = getDerivedProperties(sType);

            // class attributes
            for (SchemaProperty prop: properties)
            {
                // skip xlink properties since we get them from base type
                if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(prop.getName().getLocalPart()))
                    continue;
                
                printClassAttribute(prop);
            }
            if (sType.getSimpleVariety() == SchemaType.ATOMIC && !MySchemaTypeSystemCompiler.isGenerated(sType.getBaseType()))
                printClassAttributeSimpleType(sType);

            // constructor
            printConstructor(shortName);

            // get/set methods
            for (SchemaProperty prop: properties)
            {
                // skip xlink properties since we get them from base type
                if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(prop.getName().getLocalPart()))
                    continue;
                
                // case of choice
                if (isChoice(prop))
                {
                    // generate only one set of getters with common base type + unset
                    // common type is inferred in javaTypeForProperty
                    printPropertyGetterImpls(prop);
                    printPropertyUnSetImpl(prop);
                    
                    boolean isComplexOgcProp = isComplexOgcProperty(prop);
                    
                    // generate setter for each possible type
                    if (!prop.isReadOnly())
                    {
                        SchemaProperty[] choiceProps = prop.getType().getElementProperties();
                        for (SchemaProperty item: choiceProps)
                        {
                            if (isComplexOgcProp)
                                ((SchemaPropertyImpl)item).setNillable(OGC_PROP_NILLABLE_CODE);
                            
                            String backupName = item.getJavaPropertyName();
                            String newName = prop.getJavaPropertyName();// + "As" + item.getJavaPropertyName();
                            ((SchemaPropertyImpl)item).setJavaPropertyName(newName);
                            // don't need to reset other property parameters as this was already done in printInnerType
                            printPropertySetterImpl(item);
                            ((SchemaPropertyImpl)item).setJavaPropertyName(backupName);
                        }
                    }
                }                    
                else
                {
                    printPropertyGetterImpls(prop);
                    if (!prop.isReadOnly())
                        printPropertySetterImpls(prop);
                }
            }
            
            // if atomic simple content, also generate get/set for inline value
            // unless base type already takes care of it
            if (sType.getSimpleVariety() == SchemaType.ATOMIC && !MySchemaTypeSystemCompiler.isGenerated(sType.getBaseType()))
                printSimpleTypeGetSetImpls(sType);
        }

        printNestedTypeImpls(sType, system);
        endBlock();
    }


    void printClassAttribute(SchemaProperty prop) throws IOException
    {
        String safeVarName = javaVarNameForProperty(prop);
        
        // case of complex property with name and/or xlink attributes
        if (isComplexOgcProperty(prop) || hasName(prop))
        {
            String propType = javaTypeForProperty(prop);
            if (prop.extendsJavaArray())
            {
                propType = OGC_PROP_IFACE_NAME + "List<" + propType + ">";
                emit("protected " + propType + " " + safeVarName + " = new " + propType + "();");
                addUsedJavaType(OGC_PROP_PACKAGE_NAME + OGC_PROP_IFACE_NAME + "List");
            }
            else
            {
                String complexPropIface = OGC_PROP_IFACE_NAME + "<" + propType + ">";
                String complexPropClass = OGC_PROP_CLASS_NAME + "<" + propType + ">";
                String initializer = "";
                if (!prop.extendsJavaOption())
                    initializer = " = new " + complexPropClass + "()";
                emit("protected " + complexPropIface + " " + safeVarName + initializer + ";");
            }
        }
        else
        {
            if (prop.extendsJavaArray())
            {
                String propType = javaListTypeForProperty(prop, true);
                emit("protected " + propType + " " + safeVarName + " = new Array" + propType + "();");
                addUsedJavaType(ArrayList.class.getCanonicalName());
            }
            else
            {
                String propType = javaTypeForProperty(prop);
                if (prop.extendsJavaOption() && hasJavaPrimitiveType(prop))
                    propType = javaWrappedType(propType);
                String initializer = "";
                if (!prop.extendsJavaOption())
                {
                    String defaultVal = prop.getDefaultText();
                    if (defaultVal != null)
                    {                    
                        if (hasJavaPrimitiveType(prop))
                            initializer = " = " + defaultVal;
                        else if (propType.equals(String.class.getSimpleName()))
                            initializer = " = \"" + defaultVal + "\"";
                    }
                    else if (propType.equals(String.class.getSimpleName()))
                        initializer = " = \"\"";                    
                }
                emit("protected " + propType + " " + safeVarName + initializer + ";");
            }
        }
    }
    
    
    void printClassAttributeSimpleType(SchemaType sType) throws IOException
    {
        String javaType = javaTypeForSchemaType(sType.getBaseType());
        emit("protected " + javaType + " value;");
    }
    
    
    void printPropertyGetterImpls(SchemaProperty sProp) throws IOException
    {
        String safeVarName = javaVarNameForProperty(sProp);
        boolean several = sProp.extendsJavaArray();

        // get
        printPropertyGetterSignature(sProp, true);
        startBlock();
        if (!several && isComplexOgcProperty(sProp))
            emit("return " + safeVarName + ".getValue();");
        else
            emit("return " + safeVarName + ";");
        endBlock();

        if (several)
        {
            // getNum
            printPropertyGetNumSignature(sProp, true);
            startBlock();
            emit("if (" + safeVarName + " == null)");
            indent();
            emit("return 0;");
            outdent();
            emit("return " + safeVarName + ".size();");
            endBlock();
            
            // get by name
            if (isOgcProperty(sProp) && hasName(sProp))
            {
                printNamedPropertyGetterSignature(sProp, true);
                startBlock();
                emit("if (" + safeVarName + " == null)");
                indent();
                emit("return null;");
                outdent();
                emit("return " + safeVarName + ".get(name);");
                endBlock();
            }
        }

        else
        {
            // getProperty
            if (isComplexOgcProperty(sProp))
            {
                String propType = javaTypeForProperty(sProp);
                printOgcPropertyGetterSignature(sProp, true);
                startBlock();
                emit("if (" + safeVarName + " == null)");
                indent();
                emit(safeVarName + " = new " + OGC_PROP_CLASS_NAME + "<" + propType + ">();");
                outdent();
                emit("return " + safeVarName + ";");
                endBlock();
            }
            
            // isSet
            if (sProp.extendsJavaOption())
            {
                printPropertyIsSetSignature(sProp, true);
                startBlock();
                if (isComplexOgcProperty(sProp))
                    emit("return (" + safeVarName + " != null && (" + safeVarName + ".hasValue() || " + safeVarName + ".hasHref()));");
                else
                    emit("return (" + safeVarName + " != null);");
                endBlock();
            }
        }
    }


    void printPropertySetterImpls(SchemaProperty sProp) throws IOException
    {
        printPropertySetterImpl(sProp);
        //printPropertyAddNewImpl(sProp);
        printPropertyUnSetImpl(sProp);        
    }
    
    
    void printPropertySetterImpl(SchemaProperty sProp) throws IOException
    {
        String safeVarName = javaVarNameForProperty(sProp);
        
        printPropertySetterSignature(sProp, true);        
        startBlock();
        if (sProp.extendsJavaArray())
        {
            String itemName = safeVarName.replace("List", "");
            if (isOgcProperty(sProp) && hasName(sProp))
                emit("this." + safeVarName + ".add(name, " + itemName + ");");
            else
                emit("this." + safeVarName + ".add(" + itemName + ");");
        }
        else if (isComplexOgcProperty(sProp))
        {
            if (sProp.extendsJavaOption())
            {
                String propType = javaTypeForProperty(sProp);
                emit("if (this." + safeVarName + " == null)");
                indent();
                emit("this." + safeVarName + " = new " + OGC_PROP_CLASS_NAME + "<" + propType + ">();");
                outdent();
            }
            emit("this." + safeVarName + ".setValue(" + safeVarName + ");");
            if (isOgcProperty(sProp) && hasName(sProp))
                emit("this." + safeVarName + ".setName(name);");
        }
        else
        {
            emit("this." + safeVarName + " = " + safeVarName + ";");
        }
        endBlock();
    }
    
    
    void printPropertyUnSetImpl(SchemaProperty sProp) throws IOException
    {
        if (sProp.extendsJavaOption() && hasJavaPrimitiveType(sProp))
        {
            String safeVarName = javaVarNameForProperty(sProp);        
            printPropertyUnSetSignature(sProp, true);
            startBlock();
            emit("this." + safeVarName + " = null;");
            endBlock();
        }
    }
    
    
    void printPropertyAddNewImpl(SchemaProperty sProp) throws IOException
    {
        /*if (sProp.extendsJavaArray())
        {
            String safeVarName = javaVarNameForProperty(sProp);
            printPropertyAddNewSignature(sProp, true);
            startBlock();
            String valueType = javaTypeForProperty(sProp);
            emit("this." + listName + ".add(new " + valueType + "());");
            endBlock();
        }*/
    }


    void printNestedTypeImpls(SchemaType sType, SchemaTypeSystem system) throws IOException
    {
        SchemaType[] anonTypes = sType.getAnonymousTypes();
        for (SchemaType anonType: anonTypes)
        {
            SchemaProperty[] eltProps = anonType.getElementProperties();
            if (eltProps.length > 0)
            {
                anonType = eltProps[0].getType();
                if (!anonType.isAnonymousType())
                    continue;
            }
            
            if (anonType.isSkippedAnonymousType())
                printNestedTypeImpls(anonType, system);
            else
                printInnerTypeImpl(anonType, system, true);
        }
    }
        

    @Override
    public void printLoader(Writer writer, SchemaTypeSystem system) throws IOException
    {        
    }
}
