package glassfish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** An iterator that iterates over the elements of a list.
 * Each element will be returned the number of times of its weight.
 * hasNext() is true until each element has been returned at many
 * times as its weight.
 *
 * @author ken_admin
 */
public class WeightedCircularIterator<T> implements Iterator<T> {
    private final List<T> elements = new ArrayList<T>() ;
    private final Map<T,Integer> weights = new HashMap<T,Integer>() ;
    private final Map<T,Integer> counts = new HashMap<T,Integer>() ;
    private int current = 0 ;

    public void add( T elem, int weight ) {
        elements.add( elem ) ;
        weights.put( elem, weight ) ;
        counts.put( elem, 0 ) ;
    }

    @Override
    public boolean hasNext() {
        return !elements.isEmpty() ;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException(
                "Collection is empty, so no next element") ;
        }

        if (current > (elements.size()-1)) {
            current = 0 ;
        }

        T result = elements.get( current ) ;

        int count = counts.get( result ) ;
        count++ ;
        counts.put( result, count ) ;
        if (count == weights.get( result )) {
            elements.remove( current ) ;
        } else {
            current++ ;
        }

        return result ;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static void main( String[] args ) {
        WeightedCircularIterator<String> iter =
            new WeightedCircularIterator<String>() ;

        iter.add( "A", 5 ) ;
        iter.add( "B", 3 ) ;
        iter.add( "C", 1 ) ;
        iter.add( "D", 4 ) ;

        String[] strs = { "A", "B", "C", "D", "A", "B", "D", "A", "B", "D",
            "A", "D", "A" } ;

        for (String str : strs) {
            if (!iter.hasNext()) {
                throw new RuntimeException( "Unexpected failure of hasNext") ;
            }

            String next = iter.next() ;
            System.out.println( next ) ;
            if (!str.equals( next )) {
                throw new RuntimeException(
                    "Expected " + str + ", got " + next ) ;
            }
        }

        if (iter.hasNext()) {
            throw new RuntimeException(
                "hasNext true after all elements exhausted" ) ;
        }
    }
}
