#!/bin/bash

# Reference guide: http://habrahabr.ru/post/171493/

mvn clean release:prepare release:perform -P gpg-passphrase,sonatype-oss-release