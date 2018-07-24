# VIm Syntax Highlighting
Now you can read your Shesmu scripts in glorious colour.

Install by doing:

    sudo make install

If you are on Debian or Ubuntu:

    sudo apt-get install vim-addon-manager
    vim-addon-manager install shesmu-syntax

Otherwise,

    mkdir -p ~/.vim/ftdetect
    mkdir -p ~/.vim/syntax
    ln -s /usr/share/vim/addons/ftdetect/shesmu.vim ~/.vim/ftdetect
    ln -s /usr/share/vim/addons/syntax/shesmu.vim ~/.vim/syntax

