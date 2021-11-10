package redempt.redclaims.claim;

public enum ClaimRank {
	
	VISITOR(0),
	MEMBER(1),
	TRUSTED(2),
	OWNER(3);
	
	private int rank;
	
	ClaimRank(int rank) {
		this.rank = rank;
	}
	
	public int getRank() {
		return rank;
	}
	
}
