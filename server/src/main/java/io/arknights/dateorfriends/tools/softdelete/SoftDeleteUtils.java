package io.arknights.dateorfriends.tools.softdelete;

public final class SoftDeleteUtils {

    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    private SoftDeleteUtils() {
    }

    public static <T extends SoftDeletable> T markDeleted(T entity) {
        if (entity == null) {
            return null;
        }
        entity.setDeleted(DELETED);
        return entity;
    }

    public static boolean isDeleted(SoftDeletable entity) {
        if (entity == null || entity.getDeleted() == null) {
            return false;
        }
        return entity.getDeleted() == DELETED;
    }

}

