#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${groupId}.business.exception;

/**
 * Thrown when entity cannot be found by ID.
 */
public class EntityNotFoundException extends EntityException {

	private static final long serialVersionUID = 1L;

}