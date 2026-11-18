package com.dbfleetops.operation.job.dto;

public record BackupBinlogPosition(String file, long position) {
}
