/*
 * Copyright 2009-2012 by The Regents of the University of California
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

package edu.uci.ics.hyracks.storage.am.lsm.invertedindex.impls;

import java.util.ArrayList;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.btree.impls.RangePredicate;
import edu.uci.ics.hyracks.storage.am.common.api.ICursorInitialState;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexAccessor;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.ISearchPredicate;
import edu.uci.ics.hyracks.storage.am.common.api.IndexException;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.am.common.tuples.PermutingTupleReference;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.LSMTreeSearchCursor;
import edu.uci.ics.hyracks.storage.am.lsm.invertedindex.api.IInvertedIndexAccessor;

public class LSMInvertedIndexRangeSearchCursor extends LSMTreeSearchCursor {

    // Assuming the cursor for all deleted-keys indexes are of the same type.
    protected IIndexCursor deletedKeysBTreeCursor;
    protected ArrayList<IIndexAccessor> deletedKeysBTreeAccessors;
    protected PermutingTupleReference keysOnlyTuple;
    protected RangePredicate keySearchPred;
    
    @Override
    public void open(ICursorInitialState initState, ISearchPredicate searchPred) throws IndexException,
            HyracksDataException {
        LSMInvertedIndexRangeSearchCursorInitialState lsmInitState = (LSMInvertedIndexRangeSearchCursorInitialState) initState;
        cmp = lsmInitState.getOriginalKeyComparator();
        int numComponents = lsmInitState.getNumComponents();
        rangeCursors = new IIndexCursor[numComponents];
        for (int i = 0; i < numComponents; i++) {
            IInvertedIndexAccessor invIndexAccessor = (IInvertedIndexAccessor) lsmInitState.getIndexAccessors().get(i);
            rangeCursors[i] = invIndexAccessor.createRangeSearchCursor();
            invIndexAccessor.rangeSearch(rangeCursors[i], lsmInitState.getSearchPredicate());

        }
        
        // For searching the deleted-keys BTrees.
        deletedKeysBTreeAccessors = lsmInitState.getDeletedKeysBTreeAccessors();
        deletedKeysBTreeCursor = deletedKeysBTreeAccessors.get(0).createSearchCursor();        
        MultiComparator keyCmp = lsmInitState.getKeyComparator();
        // Project away token fields.
        int[] keyFieldPermutation = new int[keyCmp.getKeyFieldCount()];
        int numTokenFields = cmp.getKeyFieldCount() - keyCmp.getKeyFieldCount();
        for (int i = 0; i < keyCmp.getKeyFieldCount(); i++) {
            keyFieldPermutation[i] = numTokenFields + i;
        }
        keysOnlyTuple = new PermutingTupleReference(keyFieldPermutation);
        keySearchPred = new RangePredicate(keysOnlyTuple, keysOnlyTuple, true, true, keyCmp, keyCmp);
        
        searcherRefCount = lsmInitState.getSearcherRefCount();
        lsmHarness = lsmInitState.getLSMHarness();
        includeMemComponent = lsmInitState.getIncludeMemComponent();
        setPriorityQueueComparator();
        initPriorityQueue();
    }
    
    // Check deleted-keys BTrees whether they contain the key in the checkElement's tuple.
    protected boolean isDeleted(PriorityQueueElement checkElement) throws HyracksDataException {
        int end = checkElement.getCursorIndex();
        for (int i = 0; i <= end; i++) {
            deletedKeysBTreeCursor.reset();
            keysOnlyTuple.reset(checkElement.getTuple());
            try {
                deletedKeysBTreeAccessors.get(i).search(deletedKeysBTreeCursor, keySearchPred);
                if (deletedKeysBTreeCursor.hasNext()) {
                    return true;
                }
            } catch (IndexException e) {
                throw new HyracksDataException(e);
            } finally {
                deletedKeysBTreeCursor.close();
            }
        }
        return false;
    }
    
    protected void checkPriorityQueue() throws HyracksDataException {
        while (!outputPriorityQueue.isEmpty() || needPush == true) {
            if (!outputPriorityQueue.isEmpty()) {
                PriorityQueueElement checkElement = outputPriorityQueue.peek();
                // If there is no previous tuple or the previous tuple can be ignored
                if (outputElement == null) {
                    // Test the tuple is a delete tuple or not
                    if (isDeleted(checkElement)) {
                        // If the key has been deleted then pop it and set needPush to true.
                        // We cannot push immediately because the tuple may be
                        // modified if hasNext() is called
                        outputElement = outputPriorityQueue.poll();
                        needPush = true;
                    } else {
                        break;
                    }
                } else {
                    // Compare the previous tuple and the head tuple in the PQ
                    if (compare(cmp, outputElement.getTuple(), checkElement.getTuple()) == 0) {
                        // If the previous tuple and the head tuple are
                        // identical
                        // then pop the head tuple and push the next tuple from
                        // the tree of head tuple

                        // the head element of PQ is useless now
                        PriorityQueueElement e = outputPriorityQueue.poll();
                        pushIntoPriorityQueue(e);
                    } else {
                        // If the previous tuple and the head tuple are different
                        // the info of previous tuple is useless
                        if (needPush == true) {
                            pushIntoPriorityQueue(outputElement);
                            needPush = false;
                        }
                        outputElement = null;
                    }
                }
            } else {
                // the priority queue is empty and needPush
                pushIntoPriorityQueue(outputElement);
                needPush = false;
                outputElement = null;
            }
        }
    }
}
