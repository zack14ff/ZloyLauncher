package uk.betacraft.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.betacraft.auth.Credentials.AccountType;

public class Accounts {

	public List<Credentials> accounts;
	public String current;

	/** Accounts the user removed in this session, so saving doesn't bring them back from the file on disk */
	public transient Set<String> removed = new HashSet<String>();

	private Set<String> removed() {
		// Gson doesn't run field initializers
		if (this.removed == null) this.removed = new HashSet<String>();
		return this.removed;
	}

	public boolean addAccount(Credentials c) {
		if (this.accounts == null) this.accounts = new ArrayList<Credentials>();
		for (int i = 0; i < this.accounts.size(); i++) {
			if (this.accounts.get(i) == null) {
				continue;
			} else if (c.local_uuid.equals(this.accounts.get(i).local_uuid)) {
				return false;
			}
		}
		accounts.add(c);
		removed().remove(c.local_uuid);
		return true;
	}

	/**
	 * Puts fresh credentials in place of the old ones, keeping the account's position in the list.
	 * Falls back to replacing an entry with the same uuid, or appending if there is none.
	 */
	public void updateAccount(Credentials old, Credentials fresh) {
		if (this.accounts == null) this.accounts = new ArrayList<Credentials>();
		removed().remove(fresh.local_uuid);
		int index = old == null ? -1 : this.accounts.indexOf(old);
		if (index == -1) index = this.indexOf(fresh.local_uuid);

		if (index == -1) {
			this.accounts.add(fresh);
		} else {
			this.accounts.set(index, fresh);
		}

		// Drop duplicates of the same account (e.g. logging in to it twice)
		for (int i = this.accounts.size() - 1; i >= 0; i--) {
			Credentials c = this.accounts.get(i);
			if (c != fresh && (c == null || (c.local_uuid != null && c.local_uuid.equals(fresh.local_uuid)))) {
				this.accounts.remove(i);
			}
		}
	}

	public int indexOf(String uuid) {
		if (uuid == null || this.accounts == null) return -1;
		for (int i = 0; i < this.accounts.size(); i++) {
			Credentials c = this.accounts.get(i);
			if (c != null && uuid.equals(c.local_uuid)) return i;
		}
		return -1;
	}

	public boolean removeAccount(Credentials c) {
		if (c == null) return false;
		for (int i = 0; i < this.accounts.size(); i++) {
			if (this.accounts.get(i) == null) {
				continue;
			}
			// some cleanup
			if (this.accounts.get(i).local_uuid == null) {
				accounts.remove(i);
				i--;
				continue;
			}
			if (this.accounts.get(i).local_uuid.equals(c.local_uuid)) {
				accounts.remove(i);
				removed().add(c.local_uuid);
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes in accounts that only exist in the file on disk - added by another launcher
	 * sharing the folder (BetaCraft, a second copy of this one) - so saving never drops them.
	 */
	public void mergeFromDisk(Accounts disk) {
		if (disk == null || disk.accounts == null) return;
		if (this.accounts == null) this.accounts = new ArrayList<Credentials>();
		for (Credentials c : disk.accounts) {
			if (c == null || c.local_uuid == null || c.account_type == null) continue;
			if (removed().contains(c.local_uuid)) continue;
			int index = this.indexOf(c.local_uuid);
			if (index == -1) {
				this.accounts.add(c);
			} else {
				// a license refreshed by the other launcher has the newer tokens
				Credentials mine = this.accounts.get(index);
				if (c.account_type == AccountType.MICROSOFT && c.expires_at != null
						&& (mine.expires_at == null || c.expires_at > mine.expires_at)) {
					mine.refresh_token = c.refresh_token;
					mine.access_token = c.access_token;
					mine.expires_at = c.expires_at;
				}
			}
		}
	}

	public Credentials getCurrent() {
		int index = this.indexOf(this.current);
		return index == -1 ? null : this.accounts.get(index);
	}

	/** First offline account in the list, or null */
	public Credentials getFirstOffline() {
		if (this.accounts == null) return null;
		for (Credentials c : this.accounts) {
			if (c != null && c.account_type == AccountType.OFFLINE) return c;
		}
		return null;
	}

	public boolean hasOfflineNamed(String username, Credentials except) {
		if (this.accounts == null || username == null) return false;
		for (Credentials c : this.accounts) {
			if (c != null && c != except && c.account_type == AccountType.OFFLINE && username.equalsIgnoreCase(c.username)) return true;
		}
		return false;
	}

	public void setCurrent(Credentials c) {
		this.current = c.local_uuid;
	}
}
