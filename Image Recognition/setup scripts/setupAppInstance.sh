#!/bin/bash

initScript="#!/bin/bash
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=
            
/usr/bin/java -jar /home/ubuntu/ImageRecognition.jar worker"

scriptName="initWorker.sh"
echo "$initScript" > $scriptName
sudo mv $scriptName /etc/init.d/
cd /etc/init.d/
sudo chmod +x $scriptName
sudo update-rc.d $scriptName defaults
