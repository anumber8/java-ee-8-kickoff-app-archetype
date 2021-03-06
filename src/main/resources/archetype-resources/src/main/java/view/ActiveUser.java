#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${groupId}.view;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.security.enterprise.SecurityContext;

import ${groupId}.config.auth.KickoffCallerPrincipal;
import ${groupId}.model.Group;
import ${groupId}.model.Role;
import ${groupId}.model.User;

@Named
@SessionScoped
public class ActiveUser implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<String, Boolean> is = new ConcurrentHashMap<>();
	private Map<String, Boolean> can = new ConcurrentHashMap<>();
	private Map<String, Boolean> canView = new ConcurrentHashMap<>();

	private User activeUser;

	@Inject
	private SecurityContext securityContext;

	public User get() { // For use in backing beans.
		if (activeUser == null) {
			activeUser = securityContext
				.getPrincipalsByType(KickoffCallerPrincipal.class).stream()
				.map(KickoffCallerPrincipal::getUser)
				.findAny().orElse(null);
		}

		return activeUser;
	}

	public boolean isPresent() { // For use in both backing beans and EL ${symbol_pound}{activeUser.present}
		return get() != null;
	}

	public Long getId() { // For use in both backing beans and EL ${symbol_pound}{activeUser.id}
		return isPresent() ? activeUser.getId() : null;
	}

	public boolean hasGroup(Group group) { // For use in backing beans.
		return isPresent() && activeUser.getGroups().contains(group);
	}

	public boolean hasRole(Role role) { // For use in backing beans.
		return isPresent() && activeUser.getRoles().contains(role);
	}

	public boolean is(String group) { // For use in EL ${symbol_pound}{activeUser.is('ADMIN')}
		return isPresent() && is.computeIfAbsent(group, g -> activeUser.getGroups().stream().map(Group::name).anyMatch(g::equalsIgnoreCase));
	}

	public boolean can(String role) { // For use in EL ${symbol_pound}{activeUser.can('VIEW_ADMIN_PAGES')}
		return isPresent() && can.computeIfAbsent(role, r -> activeUser.getRoles().stream().map(Role::name).anyMatch(r::equalsIgnoreCase));
	}

	public boolean canView(String path) { // For use in EL ${symbol_pound}{activeUser.canView('admin/users')}
		return canView.computeIfAbsent(path, p -> securityContext.hasAccessToWebResource(p.startsWith("/") ? p : ("/" + p), "GET"));
	}

	@Override
	public String toString() { // Must print friendly name in EL ${symbol_pound}{activeUser}.
		return isPresent() ? activeUser.getFullName() : null;
	}

}