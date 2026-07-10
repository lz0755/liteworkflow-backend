#!/bin/sh
set -eu

chown mailhog:mailhog /maildir
exec su mailhog -s /bin/sh -c 'exec MailHog'
