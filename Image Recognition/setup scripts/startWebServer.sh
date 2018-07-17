#!/bin/bash

sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080

export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=

java -jar /home/ubuntu/ImageRecognition.jar server &
