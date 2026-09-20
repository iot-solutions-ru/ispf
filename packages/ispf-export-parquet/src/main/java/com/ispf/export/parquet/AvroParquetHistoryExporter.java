package com.ispf.export.parquet;

import com.ispf.core.export.HistoryParquetExporter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes historian rows as Apache Parquet through parquet-mr's Avro binding (BL-163 Wave 4).
 * Registered via {@code META-INF/services}; the server discovers it with {@link java.util.ServiceLoader}.
 */
public final class AvroParquetHistoryExporter implements HistoryParquetExporter {

    static final Schema SCHEMA = new Schema.Parser().parse("""
            {"type":"record","name":"HistoryRow","namespace":"com.ispf.history","fields":[
              {"name":"objectPath","type":["null","string"],"default":null},
              {"name":"variableName","type":["null","string"],"default":null},
              {"name":"field","type":["null","string"],"default":null},
              {"name":"timestamp","type":["null","string"],"default":null},
              {"name":"value","type":["null","double"],"default":null},
              {"name":"text","type":["null","string"],"default":null},
              {"name":"ingestedAt","type":["null","string"],"default":null}
            ]}
            """);

    @Override
    public byte[] export(List<Row> rows) throws IOException {
        ByteArrayOutputFile outputFile = new ByteArrayOutputFile();
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(SCHEMA)
                .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
                .build()) {
            for (Row sample : rows) {
                GenericRecord row = new GenericData.Record(SCHEMA);
                row.put("objectPath", sample.objectPath());
                row.put("variableName", sample.variableName());
                row.put("field", sample.field());
                row.put("timestamp", sample.timestamp());
                row.put("value", sample.value());
                row.put("text", sample.text());
                row.put("ingestedAt", sample.ingestedAt());
                writer.write(row);
            }
        }
        return outputFile.toByteArray();
    }

    @Override
    public String providerId() {
        return "parquet-mr/avro";
    }

    static final class ByteArrayOutputFile implements OutputFile {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public PositionOutputStream create(long blockSizeHint) {
            return new PositionOutputStream() {
                private long pos;

                @Override
                public long getPos() {
                    return pos;
                }

                @Override
                public void write(int b) {
                    buffer.write(b);
                    pos++;
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    buffer.write(b, off, len);
                    pos += len;
                }
            };
        }

        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) {
            buffer.reset();
            return create(blockSizeHint);
        }

        @Override
        public boolean supportsBlockSize() {
            return false;
        }

        @Override
        public long defaultBlockSize() {
            return 0;
        }

        byte[] toByteArray() {
            return buffer.toByteArray();
        }
    }
}
