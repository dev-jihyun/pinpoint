/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.mapper;

import com.navercorp.pinpoint.common.buffer.Buffer;
import com.navercorp.pinpoint.common.buffer.OffsetFixedBuffer;
import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.HbaseTableConstatns;
import com.navercorp.pinpoint.common.hbase.RowMapper;
import com.navercorp.pinpoint.common.util.BytesUtils;
import com.navercorp.pinpoint.common.util.TimeUtils;
import com.navercorp.pinpoint.common.profiler.util.TransactionId;
import com.navercorp.pinpoint.web.vo.scatter.Dot;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author netspider
 */
public class TraceIndexScatterMapper2 implements RowMapper<List<Dot>> {

    private final int responseOffsetFrom;
    private final int responseOffsetTo;

    public TraceIndexScatterMapper2(int responseOffsetFrom, int responseOffsetTo) {
        this.responseOffsetFrom = responseOffsetFrom;
        this.responseOffsetTo = responseOffsetTo;
    }

    @Override
    public List<Dot> mapRow(Result result, int rowNum) throws Exception {
        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        Cell[] rawCells = result.rawCells();
        List<Dot> list = new ArrayList<>(rawCells.length);
        for (Cell cell : rawCells) {
            final Dot dot = createDot(cell);
            if (dot != null) {
                list.add(dot);
            }
        }

        return list;
    }

    private Dot createDot(Cell cell) {

        final Buffer valueBuffer = new OffsetFixedBuffer(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
        int elapsed = valueBuffer.readVInt();

        if (elapsed < responseOffsetFrom || elapsed > responseOffsetTo) {
            return null;
        }

        int exceptionCode = valueBuffer.readSVInt();
        String agentId = valueBuffer.readPrefixedString();

        long reverseAcceptedTime = BytesUtils.bytesToLong(cell.getRowArray(), cell.getRowOffset() + HbaseTableConstatns.APPLICATION_NAME_MAX_LEN + HbaseColumnFamily.APPLICATION_TRACE_INDEX_TRACE.ROW_DISTRIBUTE_SIZE);
        long acceptedTime = TimeUtils.recoveryTimeMillis(reverseAcceptedTime);

        final int qualifierOffset = cell.getQualifierOffset();

        // TransactionId transactionId = new TransactionId(buffer,
        // qualifierOffset);

        // for temporary, used TransactionIdMapper
        TransactionId transactionId = TransactionIdMapper.parseVarTransactionId(cell.getQualifierArray(), qualifierOffset, cell.getQualifierLength());

        return new Dot(transactionId, acceptedTime, elapsed, exceptionCode, agentId);
    }
}
