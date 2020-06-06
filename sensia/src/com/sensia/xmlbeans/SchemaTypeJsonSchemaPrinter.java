package com.sensia.xmlbeans;

import java.io.CharArrayWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import org.apache.xmlbeans.SchemaGlobalElement;
import org.apache.xmlbeans.SchemaProperty;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.impl.common.NameUtil;
import com.google.gson.stream.JsonWriter;


/**
 * Prints the java code for reading and writing schema types from/to XML
 */
public final class SchemaTypeJsonSchemaPrinter extends AbstractCodePrinter
{
    static final String DEF_REF_PREFIX = "#/definitions/";
    String INDENT = "  ";
    CharArrayWriter _charBuffer = new CharArrayWriter(1024);
    String _packageName;
    List<SchemaType> processedTypes = new ArrayList<SchemaType>();
    List<String> topLevelSchemaTypes = new ArrayList<String>();
    Map<String, String> bindingClasses = new LinkedHashMap<String, String>();
    JsonWriter jsonWriter;

    
    public SchemaTypeJsonSchemaPrinter(Writer fileWriter, boolean impl)
    {
        _writer = _charBuffer;
        jsonWriter = new JsonWriter(fileWriter);
        jsonWriter.setIndent(INDENT);
    }
    

    public void startClass(String packageName) throws IOException
    {
        jsonWriter.beginObject();
        jsonWriter.name("$schema").value("http://json-schema.org/draft-07/schema#");
        jsonWriter.name("definitions").beginObject();
    }
    
    
    public void endClassAndClose() throws IOException
    {
        printBasicTypes();
        jsonWriter.endObject(); // definitions
        
        // write top level choice
        jsonWriter.name("oneOf").beginArray();
        for (String type: topLevelSchemaTypes)
        {
            jsonWriter.beginObject();
            jsonWriter.name("$ref").value(DEF_REF_PREFIX + type);
            jsonWriter.endObject();
        }
        jsonWriter.endArray();        
        
        jsonWriter.endObject(); // end root
        jsonWriter.close();
    }
    
    
    public void printTypeDef(SchemaType sType) throws IOException
    {
        // don't print global elements if they have the same name as a type
        if (sType.isDocumentType() && sType.getShortJavaName().endsWith("Element"))
            return;
        
        // don't print SWE Common basic types
        if (!sType.getName().getLocalPart().endsWith("Type") || 
            sType.isSimpleType() ||
            sType.getName().getLocalPart().equals("EncodedValuesPropertyType"))
            return;
        
        String typeName = sType.getShortJavaName();
        boolean derivedType = startTypeDef(sType);

        if (!sType.isSimpleType())
        {
            // get type properties
            // if type is global element, process sub-properties of first property
            SchemaProperty[] properties = getDerivedPropertiesWithAttrFirst(sType);
            List<String> requiredProps = new ArrayList<>();

            // start properties section
            jsonWriter.name("properties").beginObject();
            
            // add the 'type' property if not abstract
            if (!sType.isAbstract())
            {
                jsonWriter.name("type").beginObject();
                jsonWriter.name("const").value(typeName);
                jsonWriter.endObject();
                requiredProps.add("type");          
            }
            
            // write all other properties
            for (SchemaProperty prop: properties)
            {
                // skip xlink properties since we get them from base type
                if (isExtendedOgcPropertyType(sType) && XLINK_ATTRS.contains(prop.getName().getLocalPart()))
                    continue;
                
                String propName = jsonNameForProperty(prop);
                printPropertyDef(propName, prop);
                
                if (prop.getMinOccurs().intValue() > 0)
                    requiredProps.add(propName);
            }
            
            if (sType.getSimpleVariety() == SchemaType.ATOMIC && !MySchemaTypeSystemCompiler.isGenerated(sType.getBaseType()))
                printPropertyDefSimpleType(sType);
            
            jsonWriter.endObject(); // end properties
            
            // list required properties
            if (!requiredProps.isEmpty())
            {
                jsonWriter.name("required").beginArray();
                for (String prop: requiredProps)
                    jsonWriter.value(prop);
                jsonWriter.endArray();
            }
        }        
        
        if (derivedType)
        {
            jsonWriter.endObject(); // end properties wrapper
            jsonWriter.endArray(); // end allOf array
        }
        
        jsonWriter.flush();
        jsonWriter.endObject(); // end type
    }
    
    
    boolean startTypeDef(SchemaType sType) throws IOException
    {
        String typeName = sType.getShortJavaName();
        
        if (!sType.isAbstract())
            topLevelSchemaTypes.add(typeName);
        
        jsonWriter.name(typeName).beginObject();
        jsonWriter.name("type").value("object");
                
        // parent type
        boolean isAbstract = sType.isAbstract();
        SchemaType baseType;
        
        if (sType.isDocumentType())
            baseType = sType.getContentModel().getType();
        else
            baseType = sType.getBaseType();
        
        if (baseType != null && !baseType.isURType())
        {
            jsonWriter.name("allOf").beginArray();
            
            jsonWriter.beginObject();
            jsonWriter.name("$ref").value(DEF_REF_PREFIX + baseType.getShortJavaName());
            jsonWriter.endObject();
            
            jsonWriter.beginObject(); // start properties wrapper
                        
            return true;
        }
        
        return false;
    }


    void printPropertyDef(String propName, SchemaProperty prop) throws IOException
    {
        jsonWriter.name(propName).beginObject();
        
        if (prop.extendsJavaArray())
        {
            jsonWriter.name("type").value("array");
            jsonWriter.name("items").beginObject();
        }
        
        // case of complex property with name and/or xlink attributes
        if (isComplexOgcProperty(prop) || hasName(prop))
        {            
            if (hasName(prop))
            {
                jsonWriter.name("allOf").beginArray();
                
                jsonWriter.beginObject();
                jsonWriter.name("$ref").value("#/definitions/AssociationAttributeGroup");
                jsonWriter.endObject();
                
                jsonWriter.beginObject();
                printComplexPropertyContent(prop);
                jsonWriter.endObject();                
                
                jsonWriter.endArray();
            }
            else
            {
                printComplexPropertyContent(prop);
            }
        }
        else
        {
            if (prop.getType().isAnonymousType())
            {
                jsonWriter.name("type").value("inline");
            }
            else
            {
                if (!handleBasicType(prop.getType()))
                {
                    String propType = toJsonType(prop.getType());
                    jsonWriter.name("type").value(propType);
                    
                    // add string format constraint if necessary
                    if ("string".equals(propType))
                    {
                        String xsdType = prop.getType().getName().getLocalPart();
                        if ("dateTime".equals(xsdType))
                            jsonWriter.name("format").value("date-time");
                        else if ("date".equals(xsdType))
                            jsonWriter.name("format").value("date");
                        else if ("time".equals(xsdType))
                            jsonWriter.name("format").value("time");
                        else if ("anyURI".equals(xsdType))
                            jsonWriter.name("format").value("uri");
                    } 
                }
            }
        }
        
        if (prop.extendsJavaArray())
            jsonWriter.endObject();
        
        jsonWriter.endObject();
    }
    
    
    void printComplexPropertyContent(SchemaProperty prop) throws IOException
    {
        if (isChoice(prop))
            printChoiceProperty(prop);
        else if (isSubstitutable(getOgcPropertyElement(prop)))
            printSubsitutionGroupProperty(prop);
        else
        {
            String propObjType = javaTypeForProperty(prop);
            jsonWriter.name("$ref").value(DEF_REF_PREFIX + propObjType);
        }
    }
    
    
    boolean isSubstitutable(SchemaGlobalElement elt)
    {
        return elt != null && elt.substitutionGroupMembers().length != 0;
    }
    
    
    void printSubsitutionGroupProperty(SchemaProperty prop) throws IOException
    {
        QName[] members = getOgcPropertyElement(prop).substitutionGroupMembers();
        jsonWriter.name("oneOf").beginArray();
        for (QName name: members)
        {
            jsonWriter.beginObject();
            jsonWriter.name("$ref").value(DEF_REF_PREFIX + name.getLocalPart());
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
    }
    
    
    void printChoiceProperty(SchemaProperty prop) throws IOException
    {
        jsonWriter.name("oneOf").beginArray();
        
        SchemaProperty[] choiceProps = prop.getType().getElementProperties();
        for (SchemaProperty item: choiceProps)
        {
            jsonWriter.beginObject();
            jsonWriter.name("$ref").value(DEF_REF_PREFIX + item.getType().getShortJavaName());
            jsonWriter.endObject();
        }
        
        jsonWriter.endArray();
    }
    
    
    boolean handleBasicType(SchemaType sType) throws IOException
    {
        String typeName = sType.getName().getLocalPart();
        
        if ("TokenPair".equals(typeName))
        {
            startPairType();
            jsonWriter.name("items");
            printSimplePropType("string");
            return true;
        }
        
        else if ("IntegerPair".equals(typeName))
        {
            startPairType();
            jsonWriter.name("items");
            printSimplePropType("integer");
            return true;
        }
        
        else if ("RealPair".equals(typeName))
        {
            startPairType();
            jsonWriter.name("items");
            printSimplePropType("number");
            return true;
        }
        
        else if ("TimePair".equals(typeName))
        {
            startPairType();
            jsonWriter.name("items").beginObject();
            printTimePositionType();
            jsonWriter.endObject();
            return true;
        }
        
        else if ("TimePosition".equals(typeName))
        {
            printTimePositionType();
            return true;
        }
        
        else if ("NilValue".equals(typeName))
        {
            jsonWriter.name("type").value("object");
            
            jsonWriter.name("properties").beginObject();
            jsonWriter.name("reason");
            printSimplePropType("string", "uri");
            jsonWriter.name("value").beginObject();
            jsonWriter.name("oneOf").beginArray();
            printSimplePropType("string");
            printSimplePropType("number");
            jsonWriter.endArray();
            jsonWriter.endObject();
            jsonWriter.endObject(); // end properties
            
            jsonWriter.name("required").beginArray()
                .value("reason")
                .value("value")
                .endArray();
            
            return true;
        }
        
        else if ("ByteOrderType".equals(typeName))
        {
            jsonWriter.name("type").value("string");
            jsonWriter.name("enum").beginArray()
                .value("bigEndian")
                .value("littleEndian")
                .endArray();
            return true;
        }
        
        else if ("ByteEncodingType".equals(typeName))
        {
            jsonWriter.name("type").value("string");
            jsonWriter.name("enum").beginArray()
                .value("base64")
                .value("raw")
                .endArray();
            return true;
        }
        
        else if ("EncodedValuesPropertyType".equals(typeName))
        {
            jsonWriter.name("$ref").value("#/definitions/EncodedValues");
            return true;
        }
        
        else if ("Reference".equals(typeName))
        {
            jsonWriter.name("$ref").value("#/definitions/AssociationAttributeGroup");            
            return true;
        }
        
        else if ("UnitReference".equals(typeName))
        {
            jsonWriter.name("$ref").value("#/definitions/UnitReference");
            return true;
        }
        
        else if ("anyType".equals(typeName))
        {
            jsonWriter.name("type").value("object");
            jsonWriter.name("additionalProperties").value(true);
            return true;
        }
        
        return false;
    }
    
    
    void printBasicTypes() throws IOException
    {
        // write encoded values
        jsonWriter.name("EncodedValues");
        jsonWriter.beginObject();
        jsonWriter.name("type").value("array");
        jsonWriter.endObject();
        
        // write association attribute group
        jsonWriter.name("AssociationAttributeGroup");
        jsonWriter.beginObject();
        jsonWriter.name("type").value("object");
        jsonWriter.name("properties").beginObject();
        printSimpleProp("name", "string", null);
        printSimpleProp("href", "string", "uri");
        printSimpleProp("role", "string", "uri");
        printSimpleProp("arcrole", "string", "uri");
        printSimpleProp("title", "string", null);
        jsonWriter.endObject();
        jsonWriter.endObject();
        
        // unit reference
        jsonWriter.name("UnitReference").beginObject();
        jsonWriter.name("type").value("object");
        
        jsonWriter.name("allOf").beginArray();
        jsonWriter.beginObject();
        jsonWriter.name("$ref").value("#/definitions/AssociationAttributeGroup");
        jsonWriter.endObject();
        jsonWriter.beginObject();
        jsonWriter.name("properties").beginObject();
        printSimpleProp("code", "string", null);
        jsonWriter.endObject();
        jsonWriter.endObject();
        jsonWriter.endArray();        
        
        jsonWriter.name("oneOf").beginArray();
        jsonWriter.beginObject().name("required").beginArray().value("code").endArray().endObject();
        jsonWriter.beginObject().name("required").beginArray().value("href").endArray().endObject();
        jsonWriter.endArray();
        
        jsonWriter.endObject();
    }
    
    
    void startPairType() throws IOException
    {
        jsonWriter.name("type").value("array");
        jsonWriter.name("minItems").value(2);
        jsonWriter.name("maxItems").value(2);
    }
    
    
    void printTimePositionType() throws IOException
    {
        jsonWriter.name("oneOf").beginArray();
        printSimplePropType("string", "date-time");
        printSimplePropType("number");
        jsonWriter.endArray();
    }
    
    
    void printSimpleProp(String name, String type, String format) throws IOException
    {
        jsonWriter.name(name);
        printSimplePropType(type, format);
    }
    
    
    void printSimplePropType(String typeName) throws IOException
    {
        printSimplePropType(typeName, null);
    }
    
    
    void printSimplePropType(String typeName, String format) throws IOException
    {
        jsonWriter.beginObject();
        //jsonWriter.setIndent("");
        jsonWriter.name("type").value(typeName);
        if (format != null)
            jsonWriter.name("format").value(format);
        jsonWriter.endObject();
        //jsonWriter.setIndent(INDENT);
    }
    
    
    Set<String> integerTypes = new HashSet<>(Arrays.asList(
            "byte", "short", "int", "long",
            "unsignedByte", "unsignedShort", "unsignedInt", "unsignedLong",
            "integer", "positiveInteger", "nonPositiveInteger", "negativeInteger", "nonNegativeInteger"));
    Set<String> decimalTypes = new HashSet<>(Arrays.asList("float", "double", "decimal", "anySimpleType"));
    Set<String> stringTypes = new HashSet<>(Arrays.asList("string", "ID", "NCName", "Name", "token", "normalizedString", "anyURI", "dateTime", "date", "time"));
    
    String toJsonType(SchemaType xsdType)
    {
        new HashSet<>(Arrays.asList("", ""));
        
        String dataType = xsdType.getName().getLocalPart();
        
        if (integerTypes.contains(dataType))
            return "integer";
        else if (decimalTypes.contains(dataType))
            return "number";
        else if (stringTypes.contains(dataType))
            return "string";
        else
            return dataType;
    }
    
    
    String jsonNameForProperty(SchemaProperty prop)
    {
        String propName = NameUtil.lowerCamelCase(prop.getJavaPropertyName());
        
        // handle plural names
        if (prop.extendsJavaArray() && !propName.equals("quality"))
        {
            if (propName.endsWith("y"))
                propName = propName.substring(0, propName.length()-1) + "ies";
            else if (!propName.endsWith("s"))
                propName += "s";
        }
        
        return propName;
    }
    
    
    void printPropertyDefSimpleType(SchemaType sType) throws IOException
    {
        
    }
    
    
    SchemaProperty[] getDocumentTypeProperties(SchemaType sType)
    {
        SchemaType eltType = sType.getDerivedProperties()[0].getType();
        if (eltType.isAnonymousType())
           return eltType.getDerivedProperties();
        else
            return new  SchemaProperty[0];
    }
        
    
    public void addUsedJavaType(String fullJavaType)
    {
        if (fullJavaType == null || fullJavaType.endsWith("XmlObject"))
            return;
        
        if (!usedJavaTypes.contains(fullJavaType))
            usedJavaTypes.add(fullJavaType);
    }
    
    
    public static String getSchemaFileName(String packageName)
    {
        return packageName + ".json";
    }
}
