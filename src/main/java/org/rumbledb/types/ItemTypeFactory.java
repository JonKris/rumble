package org.rumbledb.types;

import org.apache.spark.ml.linalg.VectorUDT;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.CharType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.VarcharType;
import org.rumbledb.api.Item;
import org.rumbledb.context.Name;
import org.rumbledb.context.StaticContext;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.exceptions.InvalidSchemaException;
import org.rumbledb.exceptions.OurBadException;
import org.rumbledb.items.ItemFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemTypeFactory {

    public static ItemType createItemTypeFromJSoundCompactItem(Name name, Item item, StaticContext staticContext) {
        if (item.isString()) {
            String typeString = item.getStringValue();
            if (typeString.contains("=")) {
                throw new InvalidSchemaException(
                        "= is only supported for the types of object values",
                        ExceptionMetadata.EMPTY_METADATA
                );
            }
            return new ItemTypeReference(Name.createTypeNameFromLiteral(typeString, staticContext));
        }
        if (item.isArray()) {
            List<Item> members = item.getItems();
            if (members.size() != 1) {
                throw new InvalidSchemaException("Invalid JSound: " + item, ExceptionMetadata.EMPTY_METADATA);
            }
            ItemType memberType = createItemTypeFromJSoundCompactItem(null, members.get(0), staticContext);
            return new ArrayItemType(
                    null,
                    BuiltinTypesCatalogue.arrayItem,
                    new ArrayContentDescriptor(memberType),
                    null,
                    null,
                    Collections.emptyList()
            );
        }
        if (item.isObject()) {
            Map<String, FieldDescriptor> fields = new LinkedHashMap<>();
            for (String key : item.getKeys()) {
                Item value = item.getItemByKey(key);
                boolean required = false;
                if (key.startsWith("!")) {
                    key = key.substring(1);
                    required = true;
                }
                if (key.endsWith("?")) {
                    throw new InvalidSchemaException("? not supported yet", ExceptionMetadata.EMPTY_METADATA);
                }
                if (key.startsWith("@")) {
                    throw new InvalidSchemaException("@ not supported yet", ExceptionMetadata.EMPTY_METADATA);
                }
                Item defaultValueLiteral = null;
                if (value.isString() && value.getStringValue().contains("=")) {
                    String typeString = value.getStringValue();
                    int index = typeString.indexOf("=");
                    String defaultLiteral = typeString.substring(index + 1);
                    if (defaultLiteral.contains("=")) {
                        throw new InvalidSchemaException(
                                "= can only appear once in a field descriptor type reference",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }
                    typeString = typeString.substring(0, index);
                    value = ItemFactory.getInstance().createStringItem(typeString);
                    defaultValueLiteral = ItemFactory.getInstance().createStringItem(defaultLiteral);
                }

                FieldDescriptor fieldDescriptor = new FieldDescriptor();
                fieldDescriptor.setName(key);
                fieldDescriptor.setRequired(required);
                ItemType type = createItemTypeFromJSoundCompactItem(null, value, staticContext);
                fieldDescriptor.setType(type);
                fieldDescriptor.setUnique(false);
                fieldDescriptor.setDefaultValue(defaultValueLiteral);
                fields.put(key, fieldDescriptor);
            }
            return new ObjectItemType(
                    name,
                    BuiltinTypesCatalogue.objectItem,
                    true,
                    fields,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        throw new InvalidSchemaException("Invalid JSound type definition: " + item, ExceptionMetadata.EMPTY_METADATA);
    }

    public static ItemType createItemTypeFromJSoundVerboseItem(Name name, Item item, StaticContext staticContext) {
        if (!item.isObject()) {
            throw new InvalidSchemaException(
                    "A JSound verbose schema must be an object",
                    ExceptionMetadata.EMPTY_METADATA
            );
        }
        List<String> keys = item.getKeys();
        if (!keys.contains("kind")) {
            throw new InvalidSchemaException(
                    "A JSound verbose schema must contain a 'kind' field.",
                    ExceptionMetadata.EMPTY_METADATA
            );
        }
        String kind = item.getItemByKey("kind").getStringValue();
        String baseType = item.getItemByKey("baseType").getStringValue();
        if (keys.contains("name")) {
            String declaredName = item.getItemByKey("name").getStringValue();
            if (!name.equals(name.toString())) {
                throw new InvalidSchemaException(
                        "The 'name' field does not match the type's name.",
                        ExceptionMetadata.EMPTY_METADATA
                );
            }
        }
        switch (kind) {
            case "object":
                if (baseType == null) {
                    baseType = "object";
                }
                Item contentItem = item.getItemByKey("content");
                if (contentItem == null) {
                    contentItem = ItemFactory.getInstance().createArrayItem();
                }
                if (!contentItem.isArray()) {
                    throw new InvalidSchemaException(
                            "The content facet must be an array",
                            ExceptionMetadata.EMPTY_METADATA
                    );
                }
                List<Item> contents = contentItem.getItems();
                Map<String, FieldDescriptor> fields = new LinkedHashMap<>();
                for (Item c : contents) {
                    Item fieldItem = c.getItemByKey("name");
                    if (fieldItem == null) {
                        throw new InvalidSchemaException(
                                "Field descriptor is missing a name.",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }
                    if (!fieldItem.isString()) {
                        throw new InvalidSchemaException(
                                "Field descriptor must be a string.",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }
                    String fieldName = fieldItem.getStringValue();

                    Item typeItem = c.getItemByKey("type");
                    if (typeItem == null) {
                        throw new InvalidSchemaException(
                                "Field descriptor is missing a type.",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }
                    ItemType type = null;
                    if (typeItem.isString()) {
                        type = new ItemTypeReference(
                                Name.createTypeNameFromLiteral(typeItem.getStringValue(), staticContext)
                        );
                    } else if (typeItem.isObject()) {
                        type = createItemTypeFromJSoundVerboseItem(null, typeItem, staticContext);
                    } else {
                        throw new InvalidSchemaException(
                                "Field descriptor must be a string or an object.",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }

                    boolean required = false;
                    Item requiredItem = c.getItemByKey("required");
                    if (requiredItem != null && !requiredItem.isBoolean()) {
                        throw new InvalidSchemaException(
                                "'required' must be a boolean.",
                                ExceptionMetadata.EMPTY_METADATA
                        );
                    }
                    if (requiredItem != null) {
                        required = requiredItem.getBooleanValue();
                    }
                    FieldDescriptor fieldDescriptor = new FieldDescriptor();
                    fieldDescriptor.setName(fieldName);
                    fieldDescriptor.setRequired(required);
                    fieldDescriptor.setType(type);
                    fieldDescriptor.setUnique(false);
                    // fieldDescriptor.setDefaultValue(defaultValueLiteral);
                    fields.put(fieldName, fieldDescriptor);
                }
                return new ObjectItemType(
                        name,
                        BuiltinTypesCatalogue.objectItem,
                        true,
                        fields,
                        Collections.emptyList(),
                        Collections.emptyList()
                );
            case "array":
                throw new OurBadException("Kind array is not supported yet.");
            case "atomic":
                throw new OurBadException("Kind atomic is not supported yet.");
            case "union":
                throw new OurBadException("Kind unionis not supported yet.");
            default:
                throw new InvalidSchemaException(
                        "Kind '" + kind + "' does not exist.",
                        ExceptionMetadata.EMPTY_METADATA
                );
        }
    }

    public static ItemType createItemTypeFromJSONSchemaItem(Name name, Item item, StaticContext staticContext) {
        throw new OurBadException("The JSON Schema syntax is not supported yet.");
    }

    /**
     * @param signature of the wanted function item type
     * @return a function item type with the given signature
     */
    public static FunctionItemType createFunctionItemType(FunctionSignature signature) {
        return new FunctionItemType(signature);
    }

    /**
     * Create an object item type from a spark struct type (count as restriction on generic object type)
     * 
     * @param name of the new type (if null it is an anonymous type)
     * @param structType descriptor of the object
     * @return an object item type representing the type in Rumble
     */
    private static ItemType createItemTypeFromSparkStructType(StructType structType) {
        // TODO : handle type registration
        // TODO : identical anonymous types should be equivalent?
        Map<String, FieldDescriptor> content = new LinkedHashMap<>();
        for (StructField field : structType.fields()) {
            DataType filedType = field.dataType();
            ItemType mappedItemType;
            if (filedType instanceof StructType) {
                mappedItemType = createItemTypeFromSparkStructType((StructType) filedType);
            } else {
                mappedItemType = createItemType(filedType);
            }
            FieldDescriptor fieldDescriptor = new FieldDescriptor();
            fieldDescriptor.setName(field.name());
            fieldDescriptor.setType(mappedItemType);
            fieldDescriptor.setRequired(!field.nullable());
            // TODO : how to deal with duplicate keys?
            content.put(field.name(), fieldDescriptor);
        }

        return new ObjectItemType(null, BuiltinTypesCatalogue.objectItem, true, content, null, null);
    }

    private static ItemType createArrayTypeWithSparkDataTypeContent(DataType type) {
        return new ArrayItemType(
                null,
                BuiltinTypesCatalogue.arrayItem,
                new ArrayContentDescriptor(createItemType(type)),
                null,
                null,
                null
        );
    }

    public static ItemType createItemType(DataType dt) {
        if (dt instanceof StructType) {
            return createItemTypeFromSparkStructType((StructType) dt);
        }
        if (dt instanceof ArrayType) {
            return createArrayTypeWithSparkDataTypeContent(
                ((ArrayType) dt).elementType()
            );
        }
        if (dt.equals(DataTypes.StringType)) {
            return BuiltinTypesCatalogue.stringItem;
        } else if (dt instanceof VarcharType) {
            return BuiltinTypesCatalogue.stringItem;
        } else if (dt instanceof CharType) {
            return BuiltinTypesCatalogue.stringItem;
        } else if (dt.equals(DataTypes.StringType)) {
            return BuiltinTypesCatalogue.stringItem;
        } else if (dt.equals(DataTypes.BooleanType)) {
            return BuiltinTypesCatalogue.booleanItem;
        } else if (dt.equals(DataTypes.DoubleType)) {
            return BuiltinTypesCatalogue.doubleItem;
        } else if (dt.equals(DataTypes.IntegerType)) {
            return BuiltinTypesCatalogue.integerItem;
        } else if (dt.equals(DataTypes.FloatType)) {
            return BuiltinTypesCatalogue.floatItem;
        } else if (dt instanceof DecimalType) {
            return BuiltinTypesCatalogue.decimalItem;
        } else if (dt.equals(DataTypes.LongType)) {
            return BuiltinTypesCatalogue.integerItem;
        } else if (dt.equals(DataTypes.NullType)) {
            return BuiltinTypesCatalogue.nullItem;
        } else if (dt.equals(DataTypes.ShortType)) {
            return BuiltinTypesCatalogue.integerItem;
        } else if (dt.equals(DataTypes.ByteType)) {
            return BuiltinTypesCatalogue.integerItem;
        } else if (dt.equals(DataTypes.TimestampType)) {
            return BuiltinTypesCatalogue.dateTimeItem;
        } else if (dt.equals(DataTypes.DateType)) {
            return BuiltinTypesCatalogue.dateItem;
        } else if (dt.equals(DataTypes.BinaryType)) {
            return BuiltinTypesCatalogue.hexBinaryItem;
        } else if (dt instanceof VectorUDT) {
            return createArrayTypeWithSparkDataTypeContent(
                DataTypes.DoubleType
            );
        }
        throw new OurBadException("DataFrame type unsupported: " + dt);
    }
}
