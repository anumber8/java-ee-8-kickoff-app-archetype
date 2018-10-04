#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${groupId}.business.service;

import static java.util.Arrays.asList;
import static ${groupId}.model.Group.USER;
import static org.omnifaces.persistence.JPA.getOptionalSingleResult;
import static org.omnifaces.utils.security.MessageDigests.digest;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;

import ${groupId}.business.email.EmailService;
import ${groupId}.business.email.EmailTemplate;
import ${groupId}.business.email.EmailUser;
import ${groupId}.business.exception.DuplicateEntityException;
import ${groupId}.business.exception.InvalidPasswordException;
import ${groupId}.business.exception.InvalidTokenException;
import ${groupId}.business.exception.InvalidUsernameException;
import ${groupId}.model.Credentials;
import ${groupId}.model.Group;
import ${groupId}.model.LoginToken.TokenType;
import ${groupId}.model.User;
import org.omnifaces.persistence.service.BaseEntityService;

@Stateless
public class UserService extends BaseEntityService<Long, User> {

	private static final long DEFAULT_PASSWORD_RESET_EXPIRATION_TIME_IN_MINUTES = TimeUnit.HOURS.toMinutes(1);

	@Resource
	private SessionContext sessionContext;

	@Inject
	private LoginTokenService loginTokenService;

	@Inject
	private EmailService emailService;

	public void register(User user, String password, Group... additionalGroups) {
		if (findByEmail(user.getEmail()).isPresent()) {
			throw new DuplicateEntityException();
		}

		user.getGroups().add(USER);
		user.getGroups().addAll(asList(additionalGroups));
		persist(user);
	    setPassword(user, password);
	}

	@Override
	public User update(User user) {
		User existingUser = manage(user);

		if (!user.getEmail().equals(existingUser.getEmail())) { // Email changed.
			Optional<User> otherUser = findByEmail(user.getEmail());

			if (otherUser.isPresent()) {
				if (!user.equals(otherUser.get())) {
					throw new DuplicateEntityException();
				}
				else {
					// Since email verification status can be updated asynchronous, the DB status is leading.
					// Set the current user to whatever is already in the DB.
					user.setEmailVerified(otherUser.get().isEmailVerified());
				}
			}
			else {
				user.setEmailVerified(false);
			}
		}

		return super.update(user);
	}

	public void updatePassword(User user, String password) {
		User existingUser = manage(user);
		setPassword(existingUser, password);
		super.update(existingUser);
	}

	public void updatePassword(String loginToken, String password) {
		Optional<User> user = findByLoginToken(loginToken, TokenType.RESET_PASSWORD);

		if (user.isPresent()) {
			updatePassword(user.get(), password);
			loginTokenService.remove(loginToken);
		}
	}

	public void requestResetPassword(String email, String ipAddress, String callbackUrlFormat) {
		User user = findByEmail(email).orElseThrow(InvalidUsernameException::new);
		ZonedDateTime expiration = ZonedDateTime.now().plusMinutes(DEFAULT_PASSWORD_RESET_EXPIRATION_TIME_IN_MINUTES);
		String token = loginTokenService.generate(email, ipAddress, "Reset Password", TokenType.RESET_PASSWORD, expiration.toInstant());

		EmailTemplate emailTemplate = new EmailTemplate("resetPassword")
			.setToUser(new EmailUser(user))
			.setCallToActionURL(String.format(callbackUrlFormat, token));

		Map<String, Object> messageParameters = new HashMap<>();
		messageParameters.put("expiration", expiration);
		messageParameters.put("ip", ipAddress);

		emailService.sendTemplate(emailTemplate, messageParameters);
	}

	public Optional<User> findByEmail(String email) {
		return getOptionalSingleResult(createNamedTypedQuery("User.getByEmail")
			.setParameter("email", email));
	}

	public Optional<User> findByLoginToken(String loginToken, TokenType type) {
		return getOptionalSingleResult(createNamedTypedQuery("User.getByLoginToken")
			.setParameter("tokenHash", digest(loginToken, "SHA-256"))
			.setParameter("tokenType", type));
	}

	public User getByEmail(String email) {
		return findByEmail(email).orElseThrow(InvalidUsernameException::new);
	}

	public User getByEmailAndPassword(String email, String password) {
	    User user = getByEmail(email);

	    if (!user.getCredentials().isValid(password)) {
	        throw new InvalidPasswordException();
	    }

	    return user;
	}

	public User getByLoginToken(String loginToken, TokenType type) {
		return findByLoginToken(loginToken, type).orElseThrow(InvalidTokenException::new);
	}

	public User getActiveUser() {
		return findByEmail(sessionContext.getCallerPrincipal().getName()).orElse(null);
	}

	public void setPassword(User user, String password) {
		User managedUser = manage(user);
		Credentials credentials = managedUser.getCredentials();

		if (credentials == null) {
			credentials = new Credentials();
			credentials.setUser(managedUser);
		}

		credentials.setPassword(password);
	}

}