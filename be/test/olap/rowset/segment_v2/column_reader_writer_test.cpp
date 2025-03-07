// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#include "olap/rowset/segment_v2/column_reader.h"
#include "olap/rowset/segment_v2/column_writer.h"
#include "olap/tablet_schema_helper.h"
#include "olap/decimal12.h"

#include <gtest/gtest.h>
#include <iostream>

#include "common/logging.h"
#include "env/env.h"
#include "olap/olap_common.h"
#include "olap/types.h"
#include "olap/column_block.h"
#include "util/file_utils.h"
#include "runtime/mem_tracker.h"
#include "runtime/mem_pool.h"

namespace doris {
namespace segment_v2 {

class ColumnReaderWriterTest : public testing::Test {
public:
    ColumnReaderWriterTest() : _pool(&_tracker) { }
    virtual ~ColumnReaderWriterTest() {
    }
private:
    MemTracker _tracker;
    MemPool _pool;
};

template<FieldType type, EncodingTypePB encoding>
void test_nullable_data(uint8_t* src_data, uint8_t* src_is_null, int num_rows, std::string test_name) {
    using Type = typename TypeTraits<type>::CppType;
    Type* src = (Type*)src_data;
    const TypeInfo* type_info = get_type_info(type);

    ColumnMetaPB meta;

    // write data
    std::string dname = "./ut_dir/column_reader_writer_test";
    FileUtils::create_dir(dname);
    std::string fname = dname + "/" + test_name;
    {
        std::unique_ptr<WritableFile> wfile;
        auto st = Env::Default()->new_writable_file(fname, &wfile);
        ASSERT_TRUE(st.ok());

        ColumnWriterOptions writer_opts;
        writer_opts.encoding_type = encoding;
        writer_opts.compression_type = segment_v2::CompressionTypePB::LZ4F;
        writer_opts.need_zone_map = true;

        TabletColumn column(OLAP_FIELD_AGGREGATION_NONE, type);
        if (type == OLAP_FIELD_TYPE_VARCHAR) {
            column = create_varchar_key(1);
        } else if (type == OLAP_FIELD_TYPE_CHAR) {
            column = create_char_key(1);
        }
        std::unique_ptr<Field> field(FieldFactory::create(column));
        ColumnWriter writer(writer_opts, std::move(field), true, wfile.get());
        st = writer.init();
        ASSERT_TRUE(st.ok()) << st.to_string();

        for (int i = 0; i < num_rows; ++i) {
            st = writer.append(BitmapTest(src_is_null, i), src + i);
            ASSERT_TRUE(st.ok());
        }

        st = writer.finish();
        ASSERT_TRUE(st.ok());

        st = writer.write_data();
        ASSERT_TRUE(st.ok());
        st = writer.write_ordinal_index();
        ASSERT_TRUE(st.ok());
        st = writer.write_zone_map();
        ASSERT_TRUE(st.ok());

        writer.write_meta(&meta);
        ASSERT_TRUE(meta.has_zone_map_page());

        // close the file
        wfile.reset();
    }
    // read and check
    {
        // read and check
        std::unique_ptr<RandomAccessFile> rfile;
        auto st = Env::Default()->new_random_access_file(fname, &rfile);
        ASSERT_TRUE(st.ok());

        ColumnReaderOptions reader_opts;
        std::unique_ptr<ColumnReader> reader;
        st = ColumnReader::create(reader_opts, meta, num_rows, rfile.get(), &reader);
        ASSERT_TRUE(st.ok());

        ColumnIterator* iter = nullptr;
        st = reader->new_iterator(&iter);
        ASSERT_TRUE(st.ok());
        ColumnIteratorOptions iter_opts;
        OlapReaderStatistics stats;
        iter_opts.stats = &stats;
        st = iter->init(iter_opts);
        ASSERT_TRUE(st.ok());
        // sequence read
        {
            st = iter->seek_to_first();
            ASSERT_TRUE(st.ok()) << st.to_string();

            MemTracker tracker;
            MemPool pool(&tracker);
            Type vals[1024];
            Type* vals_ = vals;
            uint8_t is_null[1024];
            ColumnBlock col(type_info, (uint8_t*)vals, is_null, 1024, &pool);

            int idx = 0;
            while (true) {
                size_t rows_read = 1024;
                st = iter->next_batch(&rows_read, &col);
                ASSERT_TRUE(st.ok());
                for (int j = 0; j < rows_read; ++j) {
                    // LOG(INFO) << "is_null=" << is_null[j] << ", src_is_null[]=" << src_is_null[idx]
                        // << ", src[idx]=" << src[idx] << ", vals[j]=" << vals[j];
                    ASSERT_EQ(BitmapTest(src_is_null, idx), BitmapTest(is_null, j));
                    if (!BitmapTest(is_null, j)) {
                        if (type == OLAP_FIELD_TYPE_VARCHAR || type == OLAP_FIELD_TYPE_CHAR) {
                            Slice* src_slice = (Slice*)src_data;
                            Slice* dst_slice = (Slice*)vals_;
                            ASSERT_EQ(src_slice[idx].to_string(), dst_slice[j].to_string()) << "j:" << j;
                        } else {
                            ASSERT_EQ(src[idx], vals[j]);
                        }
                    }
                    idx++;
                }
                if (rows_read < 1024) {
                    break;
                }
            }
        }
        // random read
        {
            MemTracker tracker;
            MemPool pool(&tracker);
            Type vals[1024];
            uint8_t is_null[1024];
            ColumnBlock col(type_info, (uint8_t*)vals, is_null, 1024, &pool);

            for (int rowid = 0; rowid < num_rows; rowid += 4025) {
                st = iter->seek_to_ordinal(rowid);
                ASSERT_TRUE(st.ok());
                
                int idx = rowid;
                size_t rows_read = 1024;
                auto st = iter->next_batch(&rows_read, &col);
                ASSERT_TRUE(st.ok());
                for (int j = 0; j < rows_read; ++j) {
                    // LOG(INFO) << "is_null=" << is_null[j] << ", src_is_null[]=" << src_is_null[idx]
                        // << ", src[idx]=" << src[idx] << ", vals[j]=" << vals[j];
                    ASSERT_EQ(BitmapTest(src_is_null, idx), BitmapTest(is_null, j));
                    if (!BitmapTest(is_null, j)) {
                        if (type == OLAP_FIELD_TYPE_VARCHAR || type == OLAP_FIELD_TYPE_CHAR) {
                            Slice* src_slice = (Slice*)src_data;
                            Slice* dst_slice = (Slice*)vals;
                            ASSERT_EQ(src_slice[idx].to_string(), dst_slice[j].to_string());
                        } else {
                            ASSERT_EQ(src[idx], vals[j]);
                        }
                    }
                    idx++;
                }
            }
        }

        delete iter;
    }
}

TEST_F(ColumnReaderWriterTest, test_nullable) {
    size_t num_uint8_rows = 1024 * 1024;
    uint8_t* is_null = new uint8_t[num_uint8_rows];
    uint8_t* val = new uint8_t[num_uint8_rows];
    for (int i = 0; i < num_uint8_rows; ++i) {
        val[i] = i;
        BitmapChange(is_null, i, (i % 4) == 0);
    }

    test_nullable_data<OLAP_FIELD_TYPE_TINYINT, BIT_SHUFFLE>(val, is_null, num_uint8_rows, "null_tiny_bs");
    test_nullable_data<OLAP_FIELD_TYPE_SMALLINT, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 2, "null_smallint_bs");
    test_nullable_data<OLAP_FIELD_TYPE_INT, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 4, "null_int_bs");
    test_nullable_data<OLAP_FIELD_TYPE_BIGINT, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 8, "null_bigint_bs");
    test_nullable_data<OLAP_FIELD_TYPE_LARGEINT, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 16, "null_largeint_bs");

    float* float_vals = new float[num_uint8_rows];
    for (int i = 0; i < num_uint8_rows; ++i) {
        float_vals[i] = i;
        is_null[i] = ((i % 16) == 0);
    }
    test_nullable_data<OLAP_FIELD_TYPE_FLOAT, BIT_SHUFFLE>((uint8_t*)float_vals, is_null, num_uint8_rows, "null_float_bs");

    double* double_vals = new double[num_uint8_rows];
    for (int i = 0; i < num_uint8_rows; ++i) {
        double_vals[i] = i;
        is_null[i] = ((i % 16) == 0);
    }
    test_nullable_data<OLAP_FIELD_TYPE_DOUBLE, BIT_SHUFFLE>((uint8_t*)double_vals, is_null, num_uint8_rows, "null_double_bs");
    // test_nullable_data<OLAP_FIELD_TYPE_FLOAT, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 4, "null_float_bs");
    // test_nullable_data<OLAP_FIELD_TYPE_DOUBLE, BIT_SHUFFLE>(val, is_null, num_uint8_rows / 8, "null_double_bs");
    delete[] val;
    delete[] is_null;
    delete[] float_vals;
    delete[] double_vals;
}

TEST_F(ColumnReaderWriterTest, test_types) {
    size_t num_uint8_rows = 1024 * 1024;
    uint8_t* is_null = new uint8_t[num_uint8_rows];

    bool* bool_vals = new bool[num_uint8_rows];
    uint24_t* date_vals = new uint24_t[num_uint8_rows];
    uint64_t* datetime_vals = new uint64_t[num_uint8_rows];
    decimal12_t* decimal_vals = new decimal12_t[num_uint8_rows];
    Slice* varchar_vals = new Slice[num_uint8_rows];
    Slice* char_vals = new Slice[num_uint8_rows];
    for (int i = 0; i < num_uint8_rows; ++i) {
        bool_vals[i] = i % 2;
        date_vals[i] = i + 33;
        datetime_vals[i] = i + 33;
        decimal_vals[i] = decimal12_t(i, i); // 1.000000001

        set_column_value_by_type(OLAP_FIELD_TYPE_VARCHAR, i, (char*)&varchar_vals[i], &_pool);
        set_column_value_by_type(OLAP_FIELD_TYPE_CHAR, i, (char*)&char_vals[i], &_pool, 8);

        BitmapChange(is_null, i, (i % 4) == 0);
    }
    test_nullable_data<OLAP_FIELD_TYPE_CHAR, DICT_ENCODING>((uint8_t*)char_vals, is_null, num_uint8_rows, "null_char_bs");
    test_nullable_data<OLAP_FIELD_TYPE_VARCHAR, DICT_ENCODING>((uint8_t*)varchar_vals, is_null, num_uint8_rows, "null_varchar_bs");
    test_nullable_data<OLAP_FIELD_TYPE_BOOL, BIT_SHUFFLE>((uint8_t*)bool_vals, is_null, num_uint8_rows, "null_bool_bs");
    test_nullable_data<OLAP_FIELD_TYPE_DATE, BIT_SHUFFLE>((uint8_t*)date_vals, is_null, num_uint8_rows / 3, "null_date_bs");

    for (int i = 0; i < num_uint8_rows; ++i) {
        BitmapChange(is_null, i, (i % 16) == 0);
    }
    test_nullable_data<OLAP_FIELD_TYPE_DATETIME, BIT_SHUFFLE>((uint8_t*)datetime_vals, is_null, num_uint8_rows / 8, "null_datetime_bs");

    for (int i = 0; i< num_uint8_rows; ++i) {
        BitmapChange(is_null, i, (i % 24) == 0);
    }
    test_nullable_data<OLAP_FIELD_TYPE_DECIMAL, BIT_SHUFFLE>((uint8_t*)decimal_vals, is_null, num_uint8_rows / 12, "null_decimal_bs");

    delete[] char_vals;
    delete[] varchar_vals;
    delete[] is_null;
    delete[] bool_vals;
    delete[] date_vals;
    delete[] datetime_vals;
    delete[] decimal_vals;
}

}
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

