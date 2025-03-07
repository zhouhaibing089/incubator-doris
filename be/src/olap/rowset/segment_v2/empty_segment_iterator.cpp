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

#include "olap/rowset/segment_v2/empty_segment_iterator.h"

#include "olap/row_block2.h"

namespace doris {
namespace segment_v2 {

EmptySegmentIterator::EmptySegmentIterator(const doris::Schema &schema): _schema(schema) {}

Status EmptySegmentIterator::next_batch(RowBlockV2* block) {
    block->set_num_rows(0);
    return Status::EndOfFile("no more data in segment");
}

} // namespace segment_v2
} // namespace doris