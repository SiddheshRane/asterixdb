/*
 * Copyright 2009-2011 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.runtime.evaluators.functions.temporal;

import java.io.DataOutput;

import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt16SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt32SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt64SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt8SerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ADateTime;
import edu.uci.ics.asterix.om.base.AMutableDateTime;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.data.std.api.IDataOutputProvider;
import edu.uci.ics.hyracks.data.std.util.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class DatetimeFromUnixTimeInMsDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private final static long serialVersionUID = 1L;
    public final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "datetime_from_unix_time_in_ms", 1);

    // allowed input types
    private final static byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();
    private final static byte SER_INT8_TYPE_TAG = ATypeTag.INT8.serialize();
    private final static byte SER_INT16_TYPE_TAG = ATypeTag.INT16.serialize();
    private final static byte SER_INT32_TYPE_TAG = ATypeTag.INT32.serialize();
    private final static byte SER_INT64_TYPE_TAG = ATypeTag.INT64.serialize();

    public final static IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {

        @Override
        public IFunctionDescriptor createFunctionDescriptor() {
            return new DatetimeFromUnixTimeInMsDescriptor();
        }
    };

    /* (non-Javadoc)
     * @see edu.uci.ics.asterix.runtime.base.IScalarFunctionDynamicDescriptor#createEvaluatorFactory(edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory[])
     */
    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) throws AlgebricksException {
        return new ICopyEvaluatorFactory() {

            private static final long serialVersionUID = 1L;

            @Override
            public ICopyEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
                return new ICopyEvaluator() {

                    private DataOutput out = output.getDataOutput();
                    private ArrayBackedValueStorage argOut = new ArrayBackedValueStorage();
                    private ICopyEvaluator eval = args[0].createEvaluator(argOut);

                    // possible output types
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ADateTime> datetimeSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ADATETIME);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ANULL);

                    private AMutableDateTime aDatetime = new AMutableDateTime(0);

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        argOut.reset();
                        eval.evaluate(tuple);
                        try {
                            if (argOut.getByteArray()[0] == SER_NULL_TYPE_TAG) {
                                nullSerde.serialize(ANull.NULL, out);
                            } else {
                                if (argOut.getByteArray()[0] == SER_INT8_TYPE_TAG) {
                                    aDatetime.setValue(AInt8SerializerDeserializer.getByte(argOut.getByteArray(), 1));
                                } else if (argOut.getByteArray()[0] == SER_INT16_TYPE_TAG) {
                                    aDatetime.setValue(AInt16SerializerDeserializer.getShort(argOut.getByteArray(), 1));
                                } else if (argOut.getByteArray()[0] == SER_INT32_TYPE_TAG) {
                                    aDatetime.setValue(AInt32SerializerDeserializer.getInt(argOut.getByteArray(), 1));
                                } else if (argOut.getByteArray()[0] == SER_INT64_TYPE_TAG) {
                                    aDatetime.setValue(AInt64SerializerDeserializer.getLong(argOut.getByteArray(), 1));
                                } else {
                                    throw new AlgebricksException(
                                            "Inapplicable input type for function datetime_from_unix_time_in_ms: expecting integer or null type, but got "
                                                    + argOut.getByteArray()[0]);
                                }
                                datetimeSerde.serialize(aDatetime, out);
                            }
                        } catch (HyracksDataException hex) {
                            throw new AlgebricksException(hex);
                        }
                    }
                };
            }
        };
    }

    /* (non-Javadoc)
     * @see edu.uci.ics.asterix.om.functions.IFunctionDescriptor#getIdentifier()
     */
    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

}
