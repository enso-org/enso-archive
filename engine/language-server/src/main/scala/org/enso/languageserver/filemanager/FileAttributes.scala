package org.enso.languageserver.filemanager

import java.io.File

/**
  * A representation of the attributes of a file.
  *
  * @param creationTime creation time
  * @param lastAccessTime last access time
  * @param lastModifiedTime last modified time
  * @param kind type of [[FileSystemObject]], can be
  *   [[FileSystemObject.DirectoryTruncated]]
  * | [[FileSystemObject.FileEntry]]
  * | [[FileSystemObject.OtherEntry]]
  * @param byteSize size in bytes
  */
case class FileAttributes(
  creationTime: UTCDateTime,
  lastAccessTime: UTCDateTime,
  lastModifiedTime: UTCDateTime,
  kind: FileSystemObject,
  byteSize: Long
)

object FileAttributes {

  def fromFileSystemAttributes(
    root: File,
    path: Path,
    attrs: FileSystemApi.Attributes
  ): FileAttributes =
    FileAttributes(
      creationTime     = UTCDateTime(attrs.creationTime),
      lastAccessTime   = UTCDateTime(attrs.lastAccessTime),
      lastModifiedTime = UTCDateTime(attrs.lastModifiedTime),
      kind             = FileSystemObject.fromEntry(root, path, attrs.kind),
      byteSize         = attrs.byteSize
    )

}
