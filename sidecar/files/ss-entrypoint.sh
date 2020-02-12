#!/bin/bash

XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts

INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-proxy)

# Update X-Road configuration on startup, if necessary
if [ -f /etc/xroad/VERSION ]; then
    IMAGE_VERSION="$(cat /etc/xroad/VERSION)"
else
    echo "WARN: Current configuration version not known" >&2
    IMAGE_VERSION=
fi
if [ -n "$IMAGE_VERSION" ] && dpkg --compare-versions "$INSTALLED_VERSION" gt "$IMAGE_VERSION";
then
    echo "Updating configuration from $IMAGE_VERSION to $INSTALLED_VERSION"
    pg_ctlcluster 10 main start
    pg_isready -t 10
    dpkg-reconfigure xroad-proxy
    pg_ctlcluster 10 main stop
    nginx -s stop
    sleep 1
    echo "$INSTALLED_VERSION" >/etc/xroad/version
fi

# Set xroad-autologin software token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    su xroad -c 'echo $XROAD_TOKEN_PIN >/etc/xroad/autologin'
    unset XROAD_TOKEN_PIN
fi

# Generate internal and admin UI TLS keys and certificates on the first run
if [ ! -f /etc/xroad/ssl/internal.crt ];
then
    echo "Generating new internal TLS key and certificate"
    ARGS="-n internal -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

if [ ! -f /etc/xroad/ssl/nginx.crt ];
then
    echo "Generating new SSL key and certificate for the admin UI"
    ARGS="-n nginx -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
