SF*Park* Android code
------------
Source for [SFPark's][sfpark] Android app

License
-------
Copyright (C) 2011 [San Francisco Municipal Transportation Agency (SFMTA)][mta]

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.	If not, see <http://www.gnu.org/licenses/>.

Build Info
----------
Use the [Maps API Key Signup][maps] and replace the apiKey in: `./res/layout/main.xml`

    android update project -p . -n SFpark-for-Android -t "Google Inc.:Google APIs:7"
    ant install

[sfpark]: http://sfpark.org
[mta]: http://www.sfmta.com/cms/home/sfmta.php
[maps]: http://code.google.com/android/add-ons/google-apis/maps-api-signup.html
