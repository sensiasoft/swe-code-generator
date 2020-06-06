### Eclipse Setup Instructions

Add tools.jar to JDK:

* Window > Preferences > Java > Installed JREs > Edit
* Select tools.jar in /usr/lib/jvm/{YOUR_JDK}/lib


Build xmlbeans:

* Change maven repo location in `xmlbeans/build.xml` from `http://repo1.maven.org` to `https://repo1.maven.org` (line 25)
* Run `ant` in xmlbeans folder to donwload dependencies and generate missing sources


