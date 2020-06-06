/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are Copyright (C) 2014 Sensia Software LLC.
 All Rights Reserved.
 
 Contributor(s): 
    Alexandre Robin <alex.robin@sensiasoftware.com>
 
******************************* END LICENSE BLOCK ***************************/

package com.sensia.xmlbeans;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.xmlbeans.SchemaGlobalElement;
import org.apache.xmlbeans.SchemaParticle;
import org.apache.xmlbeans.SchemaProperty;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SystemProperties;
import org.apache.xmlbeans.impl.common.NameUtil;
import org.apache.xmlbeans.impl.schema.BuiltinSchemaTypeSystem;
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;


public abstract class AbstractCodePrinter
{
    static final String LINE_SEPARATOR = SystemProperties.getProperty("line.separator") == null ? "\n" : SystemProperties.getProperty("line.separator");
    static final String MAX_SPACES = "                                        ";
    static final int INDENT_INCREMENT = 4;    
    
    static final String LIST_TYPE = List.class.getCanonicalName();
    static final String OGC_PROP_PACKAGE_NAME = "net.opengis.";
    static final String OGC_PROP_IFACE_NAME = "OgcProperty";
    static final String OGC_PROP_CLASS_NAME = OGC_PROP_IFACE_NAME + "Impl";
    static final String OGC_LIST_TYPE = OGC_PROP_PACKAGE_NAME + OGC_PROP_IFACE_NAME + "List";
    
    static final String XLINK_NS_URI = "http://www.w3.org/1999/xlink";
    static final List<String> XLINK_ATTRS = Arrays.asList(new String[] {"type", "href", "role", "arcrole", "title", "show", "actuate", "nilReason"});
    static final int OGC_PROP_NILLABLE_CODE = 10;
    
    List<String> usedJavaTypes = new ArrayList<String>();    
    Writer _writer;
    int _indent;
    
    
    void indent()
    {
        _indent += INDENT_INCREMENT;
    }


    void outdent()
    {
        _indent -= INDENT_INCREMENT;
    }
    
    
    void startBlock() throws IOException
    {
        emit("{");
        indent();
    }


    void endBlock() throws IOException
    {
        outdent();
        emit("}");
    }


    void printJavaDoc(String sentence) throws IOException
    {
        printJavaDoc(sentence, null, null, false);
    }
    
    
    void printJavaDoc(String sentence, boolean impl) throws IOException
    {
        printJavaDoc(sentence, null, null, impl);
    }
    
    
    void printJavaDoc(String sentence, String[] params, String returnText, boolean impl) throws IOException
    {
        emit("");
        emit("");
        
        if (impl)
        {
            emit("@Override");        
        }
        else
        {
            emit("/**");
            emit(" * " + sentence);
            if (params != null)
            {
                for (String param: params)
                    emit(" * @param " + param);
            }
            if (returnText != null)
                emit(" * @return " + returnText);
            emit(" */");
        }
    }


    void printShortJavaDoc(String sentence) throws IOException
    {
        emit("/** " + sentence + " */");
    }
    
    
    void printConstructor(String shortName) throws IOException
    {
        emit("");
        emit("");
        emit("public " + shortName + "()");
        startBlock();
        endBlock();
    }


    String encodeString(String s)
    {
        StringBuffer sb = new StringBuffer();

        sb.append('"');

        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);

            if (ch == '"')
            {
                sb.append('\\');
                sb.append('\"');
            }
            else if (ch == '\\')
            {
                sb.append('\\');
                sb.append('\\');
            }
            else if (ch == '\r')
            {
                sb.append('\\');
                sb.append('r');
            }
            else if (ch == '\n')
            {
                sb.append('\\');
                sb.append('n');
            }
            else if (ch == '\t')
            {
                sb.append('\\');
                sb.append('t');
            }
            else
                sb.append(ch);
        }

        sb.append('"');

        return sb.toString();
    }


    void emit(String s) throws IOException
    {
        int indent = _indent;

        if (indent > MAX_SPACES.length() / 2)
            indent = MAX_SPACES.length() / 4 + indent / 2;

        if (indent > MAX_SPACES.length())
            indent = MAX_SPACES.length();

        _writer.write(MAX_SPACES.substring(0, indent));
        try
        {
            _writer.write(s);
        }
        catch (CharacterCodingException cce)
        {
            _writer.write(makeSafe(s));
        }
        _writer.write(LINE_SEPARATOR);
    }


    public String makeSafe(String s)
    {
        Charset charset = Charset.forName(System.getProperty("file.encoding"));
        if (charset == null)
            throw new IllegalStateException("Default character set is null!");
        CharsetEncoder cEncoder = charset.newEncoder();
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (!cEncoder.canEncode(c))
                break;
        }
        for (; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (cEncoder.canEncode(c))
                result.append(c);
            else
            {
                String hexValue = Integer.toHexString((int) c);
                switch (hexValue.length())
                {
                    case 1:
                        result.append("\\u000").append(hexValue);
                        break;
                    case 2:
                        result.append("\\u00").append(hexValue);
                        break;
                    case 3:
                        result.append("\\u0").append(hexValue);
                        break;
                    case 4:
                        result.append("\\u").append(hexValue);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        return result.toString();
    }
    
    
    public String javaStringEscape(String str)
    {
        // forbidden: \n, \r, \", \\.
        test:
        {
            for (int i = 0; i < str.length(); i++)
            {
                switch (str.charAt(i))
                {
                    case '\n':
                    case '\r':
                    case '\"':
                    case '\\':
                        break test;
                }
            }
            return str;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < str.length(); i++)
        {
            char ch = str.charAt(i);
            switch (ch)
            {
                default:
                    sb.append(ch);
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
            }
        }
        return sb.toString();
    }
    
    
    /************************************************/
    /**           Shared Helper methods            **/
    /************************************************/
        
    /**
     * Since not all schema types have java types, this skips
     * over any that don't and gives you the nearest java base type.
     */
    public static String findJavaType(SchemaType sType)
    {
        while (sType.getFullJavaName() == null)
            sType = sType.getBaseType();

        return sType.getFullJavaName();
    }
    
    
    public static boolean hasBase(SchemaType sType)
    {
        boolean hasBase;
        SchemaType baseEnumType = sType.getBaseEnumType();
        if (baseEnumType.isAnonymousType() && baseEnumType.isSkippedAnonymousType())
        {
            if (sType.getContentBasedOnType() != null)
                hasBase = sType.getContentBasedOnType().getBaseType() != baseEnumType;
            else
                hasBase = sType.getBaseType() != baseEnumType;
        }
        else
            hasBase = baseEnumType != sType;
        return hasBase;
    }
    
    
    public void addUsedJavaType(String fullJavaType)
    {
        fullJavaType = fullJavaType.replace("[]", "");
        fullJavaType = fullJavaType.replace("$", ".");
        
        if (!usedJavaTypes.contains(fullJavaType))
            usedJavaTypes.add(fullJavaType);
    }
    
    
    public String getJavaPackage(SchemaType sType, boolean impl)
    {
        String javaType = impl ? sType.getFullJavaImplName() : sType.getFullJavaName();
        return getJavaPackage(javaType, impl);
    }
    
    
    public String getJavaPackage(String javaType, boolean impl)
    {
        int lastDot = javaType.lastIndexOf('.');
        if (lastDot < 0)
            return "";
        else
            return javaType.substring(0, lastDot);
    }
    
    
    public String getShortJavaName(String fullJavaName)
    {
        if (fullJavaName == null)
            return null;
        int dotIndex = fullJavaName.lastIndexOf('.');
        if (dotIndex < 0)
            return fullJavaName;                    
        return fullJavaName.substring(dotIndex+1);
    }


    /*public static boolean isJavaPrimitive(int javaType)
    {
        if (javaType == SchemaProperty.JAVA_BIG_INTEGER)
            return true;
        
        if (javaType == SchemaProperty.JAVA_BIG_DECIMAL)
            return true;
        
        return (javaType < SchemaProperty.JAVA_FIRST_PRIMITIVE ? false : (javaType > SchemaProperty.JAVA_LAST_PRIMITIVE ? false : true));
    }*/
    
    
    public boolean hasJavaPrimitiveType(SchemaProperty sProp)
    {
        //return isJavaPrimitive(sProp.getJavaTypeCode());
        String javaType = javaFullTypeForProperty(sProp);
        
        if (javaType.equals("boolean"))
            return true;
        else if (javaType.equals("byte"))
            return true;
        else if (javaType.equals("short"))
            return true;
        else if (javaType.equals("int"))
            return true;
        else if (javaType.equals("long"))
            return true;
        else if (javaType.equals("float"))
            return true;
        else if (javaType.equals("double"))
            return true;
        
        return false;
    }


    /** Returns the wrapped type for a java primitive. */
    public String javaWrappedType(String javaType)
    {
        if (javaType.equals("int"))
            return "Integer";
        return NameUtil.upperCamelCase(javaType);
    }
    
    
    public  String javaTypeForProperty(SchemaProperty sProp)
    {
        String fullJavaName = javaFullTypeForProperty(sProp);
        return getShortJavaName(fullJavaName);  
    }
    
    
    public String javaTypeForSchemaType(SchemaType sType)
    {
        String fullJavaName = javaFullTypeForSchemaType(sType);
        return getShortJavaName(fullJavaName);
    }
    
    
    public String javaFullTypeForProperty(SchemaProperty sProp)
    {
        if (sProp.getJavaTypeCode() == SchemaProperty.JAVA_USER)
            return ((SchemaTypeImpl) sProp.getType()).getUserTypeName();
        
        if (sProp.getJavaTypeCode() == SchemaProperty.XML_OBJECT)
        {
            if (isChoice(sProp.getType()))
            {
                SchemaType commonBaseType = null;
                
                SchemaProperty[] choiceProps = sProp.getType().getElementProperties();
                for (SchemaProperty item: choiceProps)
                {
                    if (commonBaseType == null)
                        commonBaseType = item.getType();
                    else
                        commonBaseType = item.getType().getCommonBaseType(commonBaseType);
                }
                
                addUsedJavaType(commonBaseType.getFullJavaName()); 
                return commonBaseType.getFullJavaName();
            }
            else if (isOgcProperty(sProp))
                return javaFullTypeForSchemaType(getOgcPropertyType(sProp));
        }
        
        return javaFullTypeForSchemaType(sProp.javaBasedOnType());
    }
    
    
    public String javaFullTypeForSchemaType(SchemaType sType)
    {
        String fullJavaType = null;
        
        if (sType.isSimpleType())
        {
            // primitive types
            if (sType.isBuiltinType())
                fullJavaType = sType.getFullJavaName();
            
            // string enums
            else if (sType.hasStringEnumValues())
                fullJavaType = sType.getFullJavaName();
            
            // list type
            else if (sType.getListItemType() != null)
            {
                fullJavaType = javaFullTypeForSchemaType(sType.getListItemType());
                fullJavaType += "[]";
            }
            
            // TODO union type
            
            else if (sType.getShortJavaName().equals("TimePosition"))
                fullJavaType = BuiltinSchemaTypeSystem.DATETIME_JAVATYPE;
            
            else if (sType.getPrimitiveType() != null)
                fullJavaType = javaFullTypeForSchemaType(sType.getPrimitiveType());
            
            else
                fullJavaType = Object.class.getCanonicalName();
        } 
        
        // generated objects
        else if (!MySchemaTypeSystemCompiler.isGenerated(sType))
            fullJavaType = Object.class.getCanonicalName();
        else
            fullJavaType = sType.getFullJavaName();
        
        // HACK to always use GML Reference instead of SWE Reference
        if (fullJavaType.equals("net.opengis.swe.v20.Reference")) 
            fullJavaType = "net.opengis.gml.v32.Reference";
        
        // fix name
        fullJavaType = fullJavaType.replace('$', '.'); // inner class
        
        addUsedJavaType(fullJavaType);        
        return fullJavaType;
    }
    
    
    public SchemaType getOgcPropertyType(SchemaProperty sProp)
    {
        SchemaProperty[] eltProps = sProp.getType().getElementProperties();
        if (eltProps.length > 0)
            sProp = eltProps[0];
                
        return sProp.javaBasedOnType();
    }
    
    
    public SchemaType getOgcPropertyElementType(SchemaProperty sProp)
    {
        SchemaProperty[] eltProps = sProp.getType().getElementProperties();
        if (eltProps.length > 0)
            sProp = eltProps[0];
               
        SchemaType eltType = sProp.getContainerType().getTypeSystem().findDocumentType(sProp.getName());
        if (eltType == null)
            eltType = sProp.getType();
        return eltType;
    }
    
    
    public SchemaGlobalElement getOgcPropertyElement(SchemaProperty sProp)
    {
        SchemaProperty[] eltProps = sProp.getType().getElementProperties();
        if (eltProps.length > 0)
            sProp = eltProps[0];
               
        return sProp.getContainerType().getTypeSystem().findElement(sProp.getName());
    }


    public String javaListTypeForProperty(SchemaProperty sProp, boolean impl)
    {
        String fullJavaName = javaFullListTypeForProperty(sProp, impl);
        if (fullJavaName == null)
            return null;
        int dotIndex = fullJavaName.lastIndexOf('.');
        if (dotIndex < 0)
            return fullJavaName;               
        return fullJavaName.substring(dotIndex+1); 
    }
    
    
    public String javaFullListTypeForProperty(SchemaProperty sProp, boolean impl)
    {
        String propType = javaTypeForProperty(sProp);

        if (sProp.extendsJavaArray())
        {
            if (hasJavaPrimitiveType(sProp))
                propType = javaWrappedType(propType);
            
            if (isComplexOgcProperty(sProp))
            {
                addUsedJavaType(OGC_LIST_TYPE);
                return OGC_LIST_TYPE + "<" + propType + ">";
            }
            else
            {
                addUsedJavaType(LIST_TYPE);
                return LIST_TYPE + "<" + propType + ">";
            }
        }

        return propType;
    }


    public String javaVarNameForProperty(SchemaProperty sProp)
    {
        String propertyName = sProp.getJavaPropertyName();
        String safeVarName = NameUtil.nonJavaKeyword(NameUtil.lowerCamelCase(propertyName));
        // HACK for case of choice
        safeVarName = safeVarName.replaceAll("As.*", ""); 
        
        if (sProp.extendsJavaArray())
            safeVarName += "List";

        return safeVarName;
    }


    public boolean isOgcProperty(SchemaProperty sProp)
    {
        if (Character.isLowerCase(sProp.getName().getLocalPart().charAt(0)))
        {
            if (sProp.getType().getElementProperties().length == 1)
                return true;
            
            if (isChoice(sProp))
                return true;
            
            if (sProp.getType().getContentType() == SchemaType.SIMPLE_CONTENT)
                return true;
            
            if (sProp.getType().isSimpleType())
                return true;
            
            return false;
        }
        
        return false;
    }


    public boolean isComplexOgcProperty(SchemaProperty sProp)
    {
        boolean isComplex = false;
        
        // HACK for choice items, look if nillable code is set
        if (sProp.hasNillable() == OGC_PROP_NILLABLE_CODE)
            return true;
        
        if (sProp.getType().getElementProperties().length > 0)
            return true;
       
        if (!isOgcProperty(sProp))
            return false;
        
        SchemaProperty[] attrs = sProp.getType().getAttributeProperties();
        for (SchemaProperty attr: attrs)
        {
            if (attr.getJavaPropertyName().equals("Name"))
                isComplex = true;
            else if (attr.getJavaPropertyName().equals("Href"))
                isComplex = true;
        }
        
        if (isComplex && !sProp.extendsJavaArray())
            addUsedJavaType(OGC_PROP_PACKAGE_NAME + OGC_PROP_IFACE_NAME);
                    
        return isComplex;
    }
    
    
    public boolean isExtendedOgcPropertyType(SchemaType sType)
    {
        SchemaProperty[] attrs = sType.getAttributeProperties();
        for (SchemaProperty attr: attrs)
        {
            if (attr.getName().getNamespaceURI().equals(XLINK_NS_URI))
                return true;
        }
        
        return false;
    }
    
    
    public boolean hasName(SchemaProperty sProp)
    {
        SchemaProperty[] attrs = sProp.getType().getAttributeProperties();
        for (SchemaProperty attr: attrs)
        {
            if (attr.getJavaPropertyName().equals("Name"))
                return true;
        }

        return false;
    }
    
    
    public boolean isChoice(SchemaProperty sProp)
    {
        return isChoice(sProp.getType());        
    }
    
    
    public boolean isChoice(SchemaType sType)
    {
        SchemaParticle content = sType.getContentModel();
        if (content != null)
        {
            if (content.getParticleType() == SchemaParticle.CHOICE)
                return true;
            if (content.getParticleType() == SchemaParticle.SEQUENCE && 
                content.getParticleChildren().length == 1 &&
                content.getParticleChild(0).getParticleType() == SchemaParticle.CHOICE)
                return true;
        }
        
        return false;
    }
    
    
    public QName getSchemaComponentQName(SchemaType sType)
    {
        if (sType.isDocumentType())
            return sType.getDocumentElementName();
        
        else if (sType.isAnonymousType())
            return sType.getContainerField().getName();
        
        else
            return sType.getName();
    }
    
    
    public String getSchemaComponentLocalName(SchemaType sType)
    {
        QName qname = getSchemaComponentQName(sType);
        // FIX no need to format, use local name as is (otherwise we eat some chars)
        //return NameUtil.upperCamelCase(qname.getLocalPart());
        return qname.getLocalPart();
    }
    
    
    public String getPropertyValueTypeLocalName(SchemaProperty sProp)
    {
        SchemaProperty[] eltProps = sProp.getType().getElementProperties();
        if (eltProps.length == 0)
        {
            if (sProp.getType().isURType() || sProp.getType().isAnonymousType())
                return NameUtil.upperCamelCase(sProp.getName().getLocalPart());
            else
                return NameUtil.upperCamelCase(sProp.getType().getName().getLocalPart());
        }
        else
            return NameUtil.upperCamelCase(eltProps[0].getName().getLocalPart());
    }
    
    
    public boolean hasElements(SchemaType sType)
    {
        return (sType.getElementProperties().length > 0);
    }
    
    
    public boolean hasAttributes(SchemaType sType)
    {
        return (sType.getAttributeProperties().length > 0);
    }
    
    
    public boolean hasTextValue(SchemaType sType)
    {
        return sType.isSimpleType() || sType.getContentType() == SchemaType.SIMPLE_CONTENT;
    }
    
    
    protected SchemaProperty[] getDerivedProperties(SchemaType sType)
    {
        // We have to see if this is redefined, because if it is we have
        // to include all properties associated to its supertypes
        QName name = sType.getName();
        if (name != null && name.equals(sType.getBaseType().getName()))
        {
            SchemaType sType2 = sType.getBaseType();
            // Walk all the redefined types and record any properties
            // not present in sType, because the redefined types do not
            // have a generated class to represent them
            SchemaProperty[] props = sType.getDerivedProperties();
            Map<QName, SchemaProperty> propsByName = new LinkedHashMap<QName, SchemaProperty>();
            for (int i = 0; i < props.length; i++)
                propsByName.put(props[i].getName(), props[i]);
            while (sType2 != null && name.equals(sType2.getName()))
            {
                props = sType2.getDerivedProperties();
                for (int i = 0; i < props.length; i++)
                    if (!propsByName.containsKey(props[i].getName()))
                        propsByName.put(props[i].getName(), props[i]);
                sType2 = sType2.getBaseType();
            }
            return (SchemaProperty[]) propsByName.values().toArray(new SchemaProperty[0]);
        }
        else
            return sType.getDerivedProperties();
    }
    
    
    protected SchemaProperty[] getDerivedPropertiesWithAttrFirst(SchemaType sType)
    {
        SchemaProperty[] allDerivedProperties = getDerivedProperties(sType);

        ArrayList<SchemaProperty> attrProperties = new ArrayList<>();
        ArrayList<SchemaProperty> eltProperties = new ArrayList<>();
        for (SchemaProperty prop: allDerivedProperties)
        {
            if (prop.isAttribute())
                attrProperties.add(prop);
            else
                eltProperties.add(prop);
        }
        
        attrProperties.addAll(eltProperties);
        return attrProperties.toArray(new SchemaProperty[0]);        
    }


    public SchemaType findBaseType(SchemaType sType)
    {
        while (sType.getFullJavaName() == null)
            sType = sType.getBaseType();
        return sType;
    }


    public String getBaseClass(SchemaType sType)
    {
        SchemaType baseType = findBaseType(sType.getBaseType());
        
        if (!MySchemaTypeSystemCompiler.isGenerated(baseType))
            return null;
        
        addUsedJavaType(baseType.getFullJavaImplName());        
        return baseType.getShortJavaImplName();
    }
}
