package io.github.jkanie.schedkeel.job;

import org.hibernate.envers.RevisionListener;

class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setUsername(CurrentUser.name().orElse("system"));
    }
}
