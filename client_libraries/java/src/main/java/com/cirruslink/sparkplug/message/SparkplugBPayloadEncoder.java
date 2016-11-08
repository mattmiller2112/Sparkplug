/*
 * Licensed Materials - Property of Cirrus Link Solutions
 * Copyright (c) 2016 Cirrus Link Solutions LLC - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.cirruslink.sparkplug.message;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.cirruslink.sparkplug.message.model.*;
import com.cirruslink.sparkplug.protobuf.SparkplugBProto;
import com.google.protobuf.ByteString;

/**
 * A {@link PayloadDecode} implementation for encoding Sparkplug B payloads.
 */
public class SparkplugBPayloadEncoder implements PayloadEncoder <SparkplugBPayload> {
	
	private static Logger logger = LogManager.getLogger(SparkplugBPayloadEncoder.class.getName());
	
	public SparkplugBPayloadEncoder() {
		super();
	}
	
	public byte[] getBytes(SparkplugBPayload payload) throws IOException {
		
		SparkplugBProto.Payload.Builder protoMsg = SparkplugBProto.Payload.newBuilder();
		
		// Set the timestamp
		if (payload.getTimestamp() != null) {
			logger.trace("Setting time " + payload.getTimestamp());
			protoMsg.setTimestamp(payload.getTimestamp().getTime());
		}
		
		// Set the sequence number
		logger.trace("Setting sequence number " + payload.getSeq());
		protoMsg.setSeq(payload.getSeq());
		
		// Set the UUID if defined
		if (payload.getUuid() != null) {
			logger.trace("Setting the UUID " + payload.getUuid());
			protoMsg.setUuid(payload.getUuid());
		}
		
		// Set the metrics
		for (Metric metric : payload.getMetrics()) {			
			try {
				protoMsg.addMetric(convertMetric(metric));
			} catch(Exception e) {
				logger.error("Failed to add metric: " + metric.getName());
				throw new RuntimeException(e);
			}
		}

		// Set the body
		if (payload.getBody() != null) {
			logger.debug("Setting the body " + new String(payload.getBody()));
			protoMsg.setBody(ByteString.copyFrom(payload.getBody()));
		}

		return protoMsg.build().toByteArray();
	}
	
	private SparkplugBProto.Payload.Metric.Builder convertMetric(Metric metric) throws Exception {
		
		// build a metric
		SparkplugBProto.Payload.Metric.Builder builder = SparkplugBProto.Payload.Metric.newBuilder();
		
		// set the basic parameters
		logger.debug("Adding metric: " + metric.getName());
		logger.trace("    data type: " + metric.getDataType());
		builder.setName(metric.getName());
		if (metric.hasAlias()) {
			builder.setAlias(metric.getAlias());
		}
		builder.setDatatype(metric.getDataType().toIntValue());
		if (metric.getTimestamp() != null) {
			builder.setTimestamp(metric.getTimestamp().getTime());
		}

		// Set the value and metadata
		builder = setMetricValue(builder, metric);
		if (metric.getMetaData() != null) {
			logger.trace("Metadata is not null");
			builder = setMetaData(builder, metric);
		}
		
		// Set the property set
		if (metric.getPropertySet() != null) {
			logger.trace("PropertySet is not null");
			builder.setProperties(convertPropertySet(metric.getPropertySet()));
		}
		
		return builder;
	}
	
	private SparkplugBProto.Payload.Template.Parameter.Builder convertParameter(Parameter parameter) throws Exception {
		
		// build a metric
		SparkplugBProto.Payload.Template.Parameter.Builder builder = 
				SparkplugBProto.Payload.Template.Parameter.newBuilder();
		
		logger.trace("Adding parameter: " + parameter.getName());
		logger.trace("            type: " + parameter.getType());
		
		// set the name
		builder.setName(parameter.getName());

		// Set the type and value
		builder = setParameterValue(builder, parameter);
		
		return builder;
	}
	
	private SparkplugBProto.Payload.PropertySet.Builder convertPropertySet(PropertySet propertySet) throws Exception {
		SparkplugBProto.Payload.PropertySet.Builder setBuilder = SparkplugBProto.Payload.PropertySet.newBuilder();

		Map<String, PropertyValue> map = propertySet.getPropertyMap();
		for (String key : map.keySet()) {
			SparkplugBProto.Payload.PropertyValue.Builder builder = SparkplugBProto.Payload.PropertyValue.newBuilder();
			PropertyValue value = map.get(key);
			PropertyDataType type = value.getType();
			builder.setType(type.toIntValue());
			if (value.getValue() == null) {
				builder.setIsNull(true);
			} else {
				switch (type) {
					case Boolean:
						builder.setBooleanValue((Boolean) value.getValue());
						break;
					case DateTime:
						builder.setLongValue(((Date) value.getValue()).getTime());
						break;
					case Double:
						builder.setDoubleValue((Double) value.getValue());
						break;
					case Float:
						builder.setFloatValue((Float) value.getValue());
						break;
					case Int8:
						builder.setIntValue((Byte) value.getValue());
						break;
					case Int16:
					case UInt8:
						builder.setIntValue((Short) value.getValue());
						break;
					case Int32:
					case UInt16:
						builder.setIntValue((Integer) value.getValue());
						break;
					case Int64:
					case UInt32:
						builder.setLongValue((Long) value.getValue());
						break;
					case UInt64:
						builder.setLongValue(((BigInteger) value.getValue()).longValue());
						break;
					case String:
					case Text:
						builder.setStringValue((String) value.getValue());
						break;
					case PropertySet:
						builder.setPropertysetValue(convertPropertySet((PropertySet) value.getValue()));
						break;
					case PropertySetList:
						List<?> setList = (List<?>) value.getValue();
						SparkplugBProto.Payload.PropertySetList.Builder listBuilder = 
								SparkplugBProto.Payload.PropertySetList.newBuilder();
						for (Object obj : setList) {
							listBuilder.addPropertyset(convertPropertySet((PropertySet) obj));
						}
						builder.setPropertysetsValue(listBuilder);
						break;
					case Unknown:
					default:
						logger.error("Unknown DataType: " + value.getType());
						throw new Exception("Failed to convert value " + value.getType());	
				}
			}
			setBuilder.addKeys(key);
			setBuilder.addValues(builder);
		}
		return setBuilder;
	}
	
	private SparkplugBProto.Payload.Template.Parameter.Builder setParameterValue(
			SparkplugBProto.Payload.Template.Parameter.Builder builder, Parameter parameter) throws Exception {
		ParameterDataType type = parameter.getType();
		builder.setType(type.toIntValue());
		Object value = parameter.getValue();
		switch (type) {
			case Boolean:
				builder.setBooleanValue(toBoolean(value));
				break;
			case DateTime:
				builder.setLongValue(((Date) value).getTime());
				break;
			case Double:
				builder.setDoubleValue((Double) value);
				break;
			case Float:
				builder.setFloatValue((Float) value);
				break;
			case Int8:
				builder.setIntValue((Byte) value);
				break;
			case Int16:
			case UInt8:
				builder.setIntValue((Short) value);
			case Int32:
			case UInt16:
				builder.setIntValue((Integer) value);
				break;
			case Int64:
			case UInt32:
				builder.setLongValue((Long) value);
			case UInt64:
				builder.setLongValue(((BigInteger) value).longValue());
				break;
			case Text:
			case String:
				builder.setStringValue((String) value);
				break;
			case Unknown:
			default:
				logger.error("Unknown Type: " + type);
				throw new Exception("Failed to encode");

		}
		return builder;
	}

	private SparkplugBProto.Payload.Metric.Builder setMetricValue(SparkplugBProto.Payload.Metric.Builder metricBuilder,
			Metric metric) throws Exception {

		// Set the data type
		metricBuilder.setDatatype(metric.getDataType().toIntValue());

		if (metric.getValue() == null) {
			metricBuilder.setIsNull(true);
		} else {
			switch (metric.getDataType()) {
				case Boolean:
					metricBuilder.setBooleanValue(toBoolean(metric.getValue()));
					break;
				case DateTime:
					metricBuilder.setLongValue(((Date)metric.getValue()).getTime());
					break;
				case File:
					metricBuilder.setBytesValue(ByteString.copyFrom(((File) metric.getValue()).getBytes()));
					SparkplugBProto.Payload.MetaData.Builder metaDataBuilder = 
							SparkplugBProto.Payload.MetaData.newBuilder();
					metaDataBuilder.setFileName(((File) metric.getValue()).getFileName());
					metricBuilder.setMetadata(metaDataBuilder);
					break;
				case Float:
					metricBuilder.setFloatValue((Float) metric.getValue());
					break;
				case Double:
					metricBuilder.setDoubleValue((Double) metric.getValue());
					break;
				case Int8:
					metricBuilder.setIntValue(Byte.toUnsignedInt((Byte)metric.getValue()));
					break;
				case Int16:
				case UInt8:
					metricBuilder.setIntValue(Short.toUnsignedInt((Short)metric.getValue()));
					break;
				case Int32:
				case UInt16:
					metricBuilder.setIntValue((int) metric.getValue());
					break;
				case UInt32:
				case Int64:
					metricBuilder.setLongValue((Long) metric.getValue());
					break;
				case UInt64:
					metricBuilder.setLongValue(((BigInteger) metric.getValue()).longValue());
					break;
				case String:
				case Text:
				case UUID:
					metricBuilder.setStringValue((String) metric.getValue());
					break;
				case Bytes:
					metricBuilder.setBytesValue(ByteString.copyFrom((byte[]) metric.getValue()));
					break;
				case DataSet:
					DataSet dataSet = (DataSet) metric.getValue();
					SparkplugBProto.Payload.DataSet.Builder protoDataSetBuilder = 
							SparkplugBProto.Payload.DataSet.newBuilder();

					protoDataSetBuilder.setNumOfColumns(dataSet.getNumOfColumns());

					// Column names
					List<String> columnNames = dataSet.getColumnNames();
					if (columnNames != null && !columnNames.isEmpty()) {
						for (String name : columnNames) {
							// Add the column name
							protoDataSetBuilder.addColumns(name);
						}
					} else {
						throw new Exception("Invalid DataSet");
					}

					// Column types
					List<DataSetDataType> columnTypes = dataSet.getTypes();
					if (columnTypes != null && !columnTypes.isEmpty()) {
						for (DataSetDataType type : columnTypes) {
							// Add the column type
							protoDataSetBuilder.addTypes(type.toIntValue());
						}
					} else {
						throw new Exception("Invalid DataSet");
					}

					// Dataset rows
					List<Row> rows = dataSet.getRows();
					if (rows != null && !rows.isEmpty()) {
						for (Row row : rows) {
							SparkplugBProto.Payload.DataSet.Row.Builder protoRowBuilder = 
									SparkplugBProto.Payload.DataSet.Row.newBuilder();
							List<Value<?>> values = row.getValues();
							if (values != null && !values.isEmpty()) {
								for (Value<?> value : values) {
									// Add the converted element
									protoRowBuilder.addElements(convertDataSetValue(value));
								}

								logger.debug("Adding row");
								protoDataSetBuilder.addRows(protoRowBuilder);
							} else {
								throw new Exception("Invalid DataSet");
							}
						}
					}

					// Finally add the dataset
					logger.debug("Adding the dataset");
					metricBuilder.setDatasetValue(protoDataSetBuilder);
					break;
				case Template:
					Template template = (Template) metric.getValue();
					SparkplugBProto.Payload.Template.Builder templateBuilder = 
							SparkplugBProto.Payload.Template.newBuilder();
					templateBuilder.setName(template.getName());
					if (template.getVersion() != null) {
						templateBuilder.setVersion(template.getVersion());
					}
					if (template.getTemplateRef() != null) {
						templateBuilder.setTemplateRef(template.getTemplateRef());
					}
					templateBuilder.setIsDefinition(template.isDefinition());
					
					for (Metric templateMetric : template.getMetrics()) {
						templateBuilder.addMetrics(convertMetric(templateMetric));
					}
					
					for (Parameter parameter : template.getParameters()) {
						templateBuilder.addParameters(convertParameter(parameter));
					}
					metricBuilder.setTemplateValue(templateBuilder);
					break;
				case Unknown:
				default:
					logger.error("Unknown DataType: " + metric.getDataType());
					throw new Exception("Failed to encode");

			}
		}
		return metricBuilder;
	}
	
	private SparkplugBProto.Payload.Metric.Builder setMetaData(SparkplugBProto.Payload.Metric.Builder metricBuilder,
			Metric metric) throws Exception {
		
		// If the builder has been built already - use it
		SparkplugBProto.Payload.MetaData.Builder metaDataBuilder = metricBuilder.getMetadataBuilder() != null
				? metricBuilder.getMetadataBuilder()
				: SparkplugBProto.Payload.MetaData.newBuilder();
		
		MetaData metaData = metric.getMetaData();
		if (metaData.getContentType() != null) {
			metaDataBuilder.setContentType(metaData.getContentType());
		}
		metaDataBuilder.setSize(metaData.getSize());
		metaDataBuilder.setSeq(metaData.getSeq());
		if (metaData.getFileName() != null) {
			metaDataBuilder.setFileName(metaData.getFileName());
		}
		if (metaData.getFileType() != null) {
			metaDataBuilder.setFileType(metaData.getFileType());
		}
		if (metaData.getMd5() != null) {
			metaDataBuilder.setMd5(metaData.getMd5());
		}
		if (metaData.getDescription() != null) {
			metaDataBuilder.setDescription(metaData.getDescription());
		}
		metricBuilder.setMetadata(metaDataBuilder);
		
		return metricBuilder;
	}
	
	private SparkplugBProto.Payload.DataSet.DataSetValue.Builder convertDataSetValue(Value<?> value) throws Exception {
		SparkplugBProto.Payload.DataSet.DataSetValue.Builder protoValueBuilder = 
				SparkplugBProto.Payload.DataSet.DataSetValue.newBuilder();

		// Set the value
		DataSetDataType type = value.getType();
		switch (type) {
			case Int8:
				protoValueBuilder.setIntValue((Byte) value.getValue());
				break;
			case Int16:
			case UInt8:
				protoValueBuilder.setIntValue((Short) value.getValue());
				break;
			case Int32:
			case UInt16:
				protoValueBuilder.setIntValue((Integer) value.getValue());
				break;
			case Int64:
			case UInt32:
				protoValueBuilder.setLongValue((Long) value.getValue());
				break;
			case UInt64:
				protoValueBuilder.setLongValue(((BigInteger) value.getValue()).longValue());
				break;
			case Float:
				protoValueBuilder.setFloatValue((Float) value.getValue());
				break;
			case Double:
				protoValueBuilder.setDoubleValue((Double) value.getValue());
				break;
			case String:
			case Text:
				protoValueBuilder.setStringValue((String) value.getValue());
				break;
			case Boolean:
				protoValueBuilder.setBooleanValue(toBoolean(value.getValue()));
				break;
			case DateTime:
				protoValueBuilder.setLongValue(((Date) value.getValue()).getTime());
				break;
			default:
				logger.error("Unknown DataType: " + value.getType());
				throw new Exception("Failed to convert value " + value.getType());
		}

		return protoValueBuilder;
	}
	
	private Boolean toBoolean(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Integer) {
			return ((Integer)value).intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Long) {
			return ((Long)value).longValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Float) {
			return ((Float)value).floatValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Double) {
			return ((Double)value).doubleValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Short) {
			return ((Short)value).shortValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Byte) {
			return ((Byte)value).byteValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof String) {
			return Boolean.parseBoolean(value.toString());
		}
		return (Boolean)value;
	}
}
