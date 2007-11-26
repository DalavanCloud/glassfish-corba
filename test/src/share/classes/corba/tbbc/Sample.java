package corba.tbbc ;

import com.sun.corba.se.spi.orbutil.codegen.Type ;
import com.sun.corba.se.spi.orbutil.codegen.Expression ;
import static java.lang.reflect.Modifier.* ;
import static com.sun.corba.se.spi.orbutil.codegen.Wrapper.* ;

public class Sample {

    public static void main( String[] args ) {
	Type ArrayList = _import( "java.util.ArrayList" ) ;
	Type StringArray = Type._array( Type._String() ) ;
	Type System = _import( "java.lang.String" ) ;

	_class( PUBLIC, "MyClass", Type._Object() ) ; {
	    final Expression list = _data( PUBLIC, ArrayList, "list" ) ;

	    _constructor( PUBLIC )  ; {
		final Expression a = _arg( Type._String(), "a" ) ;
		final Expression b = _arg( ArrayList, "b" ) ;
		_body() ;
		    _super() ;
		    _assign( list, _call( _this(), "bar", a, b ) ) ;
		_end() ; // of constructor
	    }

	    _method( PUBLIC|STATIC, _thisClass(), "foo" ) ; {
		final Expression a = _arg( Type._String(), "a" ) ;
		_body() ;
		    _return( _new( _thisClass(), a, _new( ArrayList ) ) ) ;
		_end() ; // of method
	    }

	    _method( PUBLIC, ArrayList, "bar" ) ; {
		final Expression a = _arg( Type._String(), "a" ) ;
		final Expression b = _arg( ArrayList, "b" ) ;
		_body() ;
		    _call( b, "add", _call( a, "toLowerCase" ) ) ;
		    _return( b ) ;
		_end() ; // of method
	    }

	    _method( PUBLIC, ArrayList, "getList" ) ; {
		_body() ;
		    _return( list ) ;
		_end() ; // of method
	    }

	    _method( PUBLIC|STATIC, Type._void(), "main" ) ; {
		final Expression args = _arg( StringArray, "args" ) ;
		_body() ;
		    Expression sout = _field( System, "out" ) ;
		    Expression fooArgs0 = _call( _thisClass(), "foo", _index( args, _const( 0 ) ) ) ;
		    _call( sout, "println", _call(  fooArgs0, "getList" ) ) ;
		_end() ; // of method
	    }

	    _end() ; // of class
	}

	Class genClass = Sample.class ;
	Class cls = _generate( genClass.getClassLoader(), genClass.getProtectionDomain() ) ;
	
	try {
	    Method m = cls.getDeclaredMethod( cls, "main", String[].class ) ;
	    m.invoke( null, args ) ;
	} catch (Exception exc) {
	    System.out.println( "Exception: " + exc ) ;
	    exc.printStackTrace() ;
	}
    }
}
